package com.threadwork.compiler.api

import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeText
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTextLanguageId
import com.threadwork.core.model.getElementById
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json

data class VirtualFile(
    var path: String,
    var content: String,
)

interface FsStorage {
    fun store(document: ThreadworkDocument, node: Node): List<VirtualFile>

    fun restore(document: ThreadworkDocument, chunk: List<VirtualFile>): ThreadworkDocument
}

fun GeneratedFile.toVirtualFile(): VirtualFile =
    VirtualFile(path, content)

fun GeneratedProject.toVirtualFiles(): List<VirtualFile> =
    files.map { it.toVirtualFile() }

fun VirtualFile.toGeneratedFile(
    originNodeId: NodeId? = null,
    reason: String = "Virtual file",
    elementKind: GeneratedElementKind = GeneratedElementKind.ProjectLayout,
): GeneratedFile =
    GeneratedFile(
        path = path,
        content = content,
        originNodeId = originNodeId,
        reason = reason,
        elementKind = elementKind,
    )

fun List<VirtualFile>.toGeneratedFiles(
    originNodeId: NodeId? = null,
    reason: String = "Virtual files",
    elementKind: GeneratedElementKind = GeneratedElementKind.ProjectLayout,
): List<GeneratedFile> =
    map { it.toGeneratedFile(originNodeId, reason, elementKind) }

fun List<VirtualFile>.writeTo(directory: Path) {
    forEach { file ->
        val target = directory.resolve(file.path)
        target.parent?.let(Files::createDirectories)
        Files.writeString(target, file.content)
    }
}

class ThreadworkDocumentFilesystemStorage(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : FsStorage {
    override fun store(document: ThreadworkDocument, node: Node): List<VirtualFile> {
        val files = mutableListOf<VirtualFile>()
        fun visit(current: Node) {
            files += storeSimpleNode(document, current)
            current.children
                .mapNotNull(document::getElementById)
                .forEach(::visit)
        }
        visit(node)
        return files
    }

    override fun restore(document: ThreadworkDocument, chunk: List<VirtualFile>): ThreadworkDocument {
        val filesByPath = chunk.associateBy { it.path.trim('/') }
        val nodeFiles = filesByPath
            .filterKeys { it.endsWith("/metadata.json") || it.endsWith("/link.json") }
            .toSortedMap()
        val restored = nodeFiles.mapValues { (path, file) ->
            val directory = path.substringBeforeLast("/", "")
            decodeNode(file.content).also { node ->
                if (!node.isLink) restoreTextFields(node, filesByPath, directory)
            }
        }
        if (restored.isEmpty()) return document

        restored.values.firstOrNull { it.parentId == null }?.let { root ->
            document.rootNodeId = root.id
            document.name = root.name
        }
        restored.values.forEach { document.nodes[it.id] = it }
        repairParentChildReferences(document)
        repairLinkReferences(document)
        return document
    }

    private fun storeSimpleNode(document: ThreadworkDocument, node: Node): List<VirtualFile> {
        val path = nodeFullPath(document, node)
        if (node.isLink) {
            return listOf(VirtualFile("$path/link.json", encodeNode(node)))
        }
        val metadataNode = node.copy(text = node.text.withoutContent())
        return listOf(
            VirtualFile("$path/instantiation.${textExtension(document, node, NodeTextSection.Instantiation)}", node.text.instantiation),
            VirtualFile("$path/declaration.${textExtension(document, node, NodeTextSection.Declaration)}", node.text.declaration),
            VirtualFile("$path/spec.${textExtension(document, node, NodeTextSection.Specification)}", node.text.specification),
            VirtualFile("$path/tests.${textExtension(document, node, NodeTextSection.Tests)}", node.text.tests),
            VirtualFile("$path/usage.${textExtension(document, node, NodeTextSection.AiInstructions)}", node.text.aiInstructions),
            VirtualFile("$path/metadata.json", encodeNode(metadataNode)),
        )
    }

    private fun encodeNode(node: Node): String =
        json.encodeToString(Node.serializer(), node)

    private fun decodeNode(content: String): Node =
        json.decodeFromString(Node.serializer(), content)

    private fun restoreTextFields(node: Node, filesByPath: Map<String, VirtualFile>, directory: String) {
        fun content(prefix: String): Pair<String, String>? =
            filesByPath.entries
                .firstOrNull { (path, _) -> path.startsWith("$directory/$prefix.") }
                ?.let { (path, file) -> file.content to path.substringAfterLast('.', "") }

        content("instantiation")?.let { (value, extension) ->
            node.text.instantiation = value
            if (node.text.instantiationLanguageId.isBlank()) node.text.instantiationLanguageId = languageForExtension(extension)
        }
        content("declaration")?.let { (value, extension) ->
            node.text.declaration = value
            if (node.text.declarationLanguageId.isBlank()) node.text.declarationLanguageId = languageForExtension(extension)
        }
        content("spec")?.let { (value, extension) ->
            node.text.specification = value
            if (node.text.specificationLanguageId.isBlank()) node.text.specificationLanguageId = languageForExtension(extension)
        }
        content("tests")?.let { (value, extension) ->
            node.text.tests = value
            if (node.text.testsLanguageId.isBlank()) node.text.testsLanguageId = languageForExtension(extension)
        }
        content("usage")?.let { (value, extension) ->
            node.text.aiInstructions = value
            if (node.text.aiInstructionsLanguageId.isBlank()) node.text.aiInstructionsLanguageId = languageForExtension(extension)
        }
    }

    private fun textExtension(document: ThreadworkDocument, node: Node, section: NodeTextSection): String {
        val sectionLanguageId = document.effectiveTextLanguageId(node.id, section)
        if (section in setOf(NodeTextSection.Instantiation, NodeTextSection.Declaration)) {
            node.technology.fileExtension.trim().trimStart('.').takeIf { it.isNotBlank() }?.let { return it }
        }
        return extensionForLanguage(sectionLanguageId.ifBlank { document.effectiveLanguageId(node.id) })
    }

    private fun repairParentChildReferences(document: ThreadworkDocument) {
        document.nodes.values.forEach { node ->
            node.children.removeAll { childId -> childId !in document.nodes || childId == node.id }
            if (node.id == document.rootNodeId) {
                node.parentId = null
            } else if (node.parentId !in document.nodes || node.parentId == node.id) {
                node.parentId = document.rootNodeId
            }
        }
        document.nodes.values.forEach { node ->
            val parent = node.parentId?.let(document.nodes::get) ?: return@forEach
            if (node.id !in parent.children) parent.children += node.id
        }
    }

    private fun repairLinkReferences(document: ThreadworkDocument) {
        document.nodes.values.forEach {
            it.incomingLinks.clear()
            it.outgoingLinks.clear()
        }
        document.nodes.values.filter { it.isLink }.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            document.nodes[link.sourceNodeId]?.let { source ->
                ensurePort(source, link.sourcePortName.ifBlank { "out" }, PortDirection.Output)
                if (linkNode.id !in source.outgoingLinks) source.outgoingLinks += linkNode.id
            }
            document.nodes[link.targetNodeId]?.let { target ->
                ensurePort(target, link.targetPortName.ifBlank { "in" }, PortDirection.Input)
                if (linkNode.id !in target.incomingLinks) target.incomingLinks += linkNode.id
            }
        }
    }

    private fun ensurePort(node: Node, name: String, direction: PortDirection) {
        if (node.ports.any { it.name == name && it.direction == direction }) return
        val baseId = "${direction.name.lowercase()}_${name.safePathSegment()}"
        var id = baseId
        var index = 2
        while (node.ports.any { it.id == id }) {
            id = "${baseId}_$index"
            index++
        }
        node.ports += com.threadwork.core.model.NodePort(id, name, direction)
    }
}

private fun NodeText.withoutContent(): NodeText =
    copy(
        instantiation = "",
        declaration = "",
        specification = "",
        tests = "",
        aiInstructions = "",
    )

private fun nodeFullPath(document: ThreadworkDocument, node: Node): String {
    val ancestors = mutableListOf<Node>()
    var current: Node? = node
    val visited = mutableSetOf<NodeId>()
    while (current != null && current.id !in visited) {
        visited += current.id
        ancestors += current
        current = current.parentId?.let(document::getElementById)
    }
    return ancestors
        .asReversed()
        .joinToString("/") { it.name.safePathSegment().ifBlank { it.id.value.safePathSegment() } }
}

private fun String.safePathSegment(): String =
    trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { "node" }

private fun extensionForLanguage(languageId: String): String =
    when (languageId.trim().lowercase()) {
        "kotlin" -> "kt"
        "javascript", "js", "nodejs" -> "js"
        "typescript", "ts" -> "ts"
        "php" -> "php"
        "markdown", "md" -> "md"
        "json" -> "json"
        "csv" -> "csv"
        "yaml", "yml" -> "yaml"
        "xml" -> "xml"
        "html" -> "html"
        "css" -> "css"
        "plain", "text", "txt", "" -> "txt"
        else -> "txt"
    }

private fun languageForExtension(extension: String): String =
    when (extension.trim().lowercase()) {
        "kt" -> "kotlin"
        "js" -> "javascript"
        "ts" -> "typescript"
        "php" -> "php"
        "md" -> "markdown"
        "json" -> "json"
        "csv" -> "csv"
        "yaml", "yml" -> "yaml"
        "xml" -> "xml"
        "html" -> "html"
        "css" -> "css"
        else -> "plain"
    }
