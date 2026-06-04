package com.orchestra.compiler.api

import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import java.nio.file.Files
import java.nio.file.Path

enum class GeneratedElementKind {
    TerminalEntity,
    CompositeEntity,
    Link,
    MagicFile,
    StaticFile,
    CompilerTemplate,
    Runtime,
    ProjectLayout,
}

const val ANY_LANGUAGE_ID = "any"

data class CompilerTechnology(
    val languageId: String,
    val technologyId: String,
)

interface CompilerPlugin {
    val id: String
    val displayName: String
    val supportedLanguageIds: Set<String> get() = emptySet()
    val supportedTechnologyIds: Set<String> get() = emptySet()
    val providedTechnologies: List<CompilerTechnology>
        get() = supportedLanguageIds.flatMap { languageId ->
            supportedTechnologyIds.map { technologyId -> CompilerTechnology(languageId, technologyId) }
        }
    val magicFileNames: Set<String> get() = emptySet()

    fun supports(document: InflowDocument): Boolean
    fun validate(document: InflowDocument): List<Diagnostic>

    fun linkStereotype(document: InflowDocument, linkNode: Node): LinkStereotype =
        LinkClassifier.classify(document, linkNode)

    fun generatedDeclarationFor(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (node.kind) {
            NodeKind.Node -> getNodeDeclaration(document, node, options)
            NodeKind.Processor -> getProcessorDeclaration(document, node, options)
            NodeKind.Link -> getLinkDeclaration(document, node, options)
            NodeKind.Group -> getGroupDeclaration(document, node, options)
            NodeKind.Note -> getNoteDeclaration(document, node, options)
        }

    fun generatedInstantiationFor(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (node.kind) {
            NodeKind.Node -> getNodeInstantiation(document, node, options)
            NodeKind.Processor -> getProcessorInstantiation(document, node, options)
            NodeKind.Link -> getLinkInstantiation(document, node, options)
            NodeKind.Group -> getGroupInstantiation(document, node, options)
            NodeKind.Note -> getNoteInstantiation(document, node, options)
        }

    fun getNodeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    fun getNodeInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNodeDeclaration(document, node, options)

    fun getProcessorDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    fun getProcessorInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getProcessorDeclaration(document, node, options)

    fun getLinkDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    fun getLinkInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getLinkDeclaration(document, node, options)

    fun getGroupDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    fun getGroupInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getGroupDeclaration(document, node, options)

    fun getNoteDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    fun getNoteInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNoteDeclaration(document, node, options)

    fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        magicFileNames.toList()

    fun generateTerminalEntity(document: InflowDocument, node: Node, options: CompilerOptions): List<GeneratedFile> =
        emptyList()

    fun generateCompositeEntity(document: InflowDocument, node: Node, options: CompilerOptions): List<GeneratedFile> =
        emptyList()

    fun generateLink(document: InflowDocument, linkNode: Node, options: CompilerOptions): List<GeneratedFile> =
        emptyList()

    fun generateMagicFile(document: InflowDocument, node: Node, options: CompilerOptions): GeneratedFile? =
        null

    fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        ClassifiedFilesystemLayoutStrategy

    fun compile(document: InflowDocument, options: CompilerOptions = CompilerOptions()): CompilationResult
}

data class CompilerOptions(
    val projectName: String? = null,
    val scopeNodeIds: Set<NodeId> = emptySet(),
)

data class CompilationResult(
    val generatedProject: GeneratedProject?,
    val diagnostics: List<Diagnostic>,
    val success: Boolean,
)

data class GeneratedProject(
    val name: String,
    val files: List<GeneratedFile>,
) {
    fun writeTo(directory: Path) {
        files.forEach { file ->
            val target = directory.resolve(file.path)
            target.parent?.let(Files::createDirectories)
            Files.writeString(target, file.content)
        }
    }
}

data class GeneratedFile(
    val path: String,
    val content: String,
    val originNodeId: NodeId?,
    val reason: String,
    val elementKind: GeneratedElementKind = GeneratedElementKind.TerminalEntity,
)

interface LayoutStrategy {
    val id: String
    val displayName: String

    fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject
}

object ClassifiedFilesystemLayoutStrategy : LayoutStrategy {
    override val id: String = "classified-filesystem"
    override val displayName: String = "Classified filesystem layout"

    override fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files)
}

object DirectFileSystemHomorphismLayoutStrategy : LayoutStrategy {
    override val id: String = "direct-file-system-homomorphism"
    override val displayName: String = "Direct file-system homomorphism"

    override fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject {
        val rootPrefix = projectName.trim().ifBlank { document.name.ifBlank { "project" } }
        val remapped = files.mapNotNull { file ->
            val node = file.originNodeId?.let(document::getElementById)
            when {
                node == null -> file.copy(path = normalizeDirectPath(file.path))
                node.isLink -> null
                else -> file.copy(path = directPathForNode(document, node, file, rootPrefix))
            }
        }
        return GeneratedProject(projectName, remapped.distinctBy { it.path })
    }
}

object SourceSetLayoutStrategy : LayoutStrategy {
    override val id: String = "source-set"
    override val displayName: String = "Source set layout"

    override fun layout(document: InflowDocument, projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(
            projectName,
            files.map { file ->
                if (file.path.contains('/')) {
                    file
                } else {
                    file.copy(path = "src/main/resources/${file.path}")
                }
            },
        )
}

private fun normalizeDirectPath(path: String): String =
    path.trim().replace('\\', '/').trimStart('/')

private fun directPathForNode(document: InflowDocument, node: Node, file: GeneratedFile, rootPrefix: String): String {
    val segments = node.directLayoutSegments(document, rootPrefix)
    val extension = file.path.substringAfterLast('.', missingDelimiterValue = "txt")
    val fileName = if (node.children.isNotEmpty()) {
        "index.$extension"
    } else {
        node.directLayoutFileName(extension)
    }
    return (segments + fileName).joinToString("/")
}

private fun Node.directLayoutSegments(document: InflowDocument, rootPrefix: String): List<String> {
    val segments = mutableListOf(rootPrefix)
    var currentParentId = parentId
    val parents = mutableListOf<Node>()
    while (currentParentId != null) {
        val parent = document.getElementById(currentParentId) ?: break
        if (parent.id == document.rootNodeId) break
        parents += parent
        currentParentId = parent.parentId
    }
    parents.asReversed().filter { it.children.isNotEmpty() }.forEach { segments += it.safeNodeSegment() }
    return segments
}

private fun Node.directLayoutFileName(extension: String): String =
    "${safeNodeSegment()}.${extension.trimStart('.').ifBlank { "txt" }}"

private fun Node.safeNodeSegment(): String =
    name.trim()
        .replace(Regex("[^A-Za-z0-9_.-]+"), "_")
        .trim('_')
        .ifBlank { id.value.take(12) }
