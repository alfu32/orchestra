package com.threadwork.compiler.filesystem

import com.threadwork.compiler.api.ANY_LANGUAGE_ID
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.GeneratedProject
import com.threadwork.compiler.api.NodeCompilerContext
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.generic.GenericCompiler
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.rootNode

/**
 * A project-level compiler that mirrors the design tree to the output folder.
 * Technology-specific descendants are delegated to their respective compilers;
 * generic leaf files remain literal files beside those generated artifacts.
 */
class FilesystemCompiler : GenericCompiler() {
    override val id: String = "filesystem"
    override val displayName: String = "Filesystem Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("filesystem", "file", "generic")
    override val providedTechnologies: List<CompilerTechnology> = listOf(
        CompilerTechnology(ANY_LANGUAGE_ID, "filesystem"),
        CompilerTechnology(ANY_LANGUAGE_ID, "file"),
        CompilerTechnology(ANY_LANGUAGE_ID, "generic"),
    )
    override val genericDefaultLayoutStrategy = DirectFileSystemHomorphismLayoutStrategy

    override fun supports(document: ThreadworkDocument): Boolean = true

    companion object {
        /**
         * A direct filesystem root or a foreign standalone file needs the
         * aggregate compiler even if its enclosing workflow has one language.
         */
        fun shouldAggregate(document: ThreadworkDocument): Boolean {
            val root = document.rootNode()
            if (root.technology.compilerId.trim() == "filesystem") return true
            if (document.effectiveLayoutStrategyId(root.id) == DirectFileSystemHomorphismLayoutStrategy.id) return true
            val rootLanguage = document.effectiveLanguageId(root.id).trim()
            return document.nodes.values.any { node ->
                val technologyId = document.effectiveTechnologyId(node.id).trim()
                !node.isLink &&
                    node.children.isEmpty() &&
                    (
                        technologyId in setOf("filesystem", "file", "generic") ||
                            document.effectiveLayoutStrategyId(node.id) == SingleFileLayoutStrategy.id &&
                                document.effectiveLanguageId(node.id).trim().isNotBlank() &&
                                document.effectiveLanguageId(node.id).trim() != rootLanguage
                    )
            }
        }
    }

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        super.staticFileFor(context)?.let { return it }
        val node = context.node
        val genericLeaf = !node.isLink && node.children.isEmpty()
        val source = node.text.declaration.ifBlank { node.text.specification }
        if (!genericLeaf || source.isBlank()) return null

        return GeneratedFile(
            path = literalFilePath(context.document, node),
            content = source,
            originNodeId = node.id,
            reason = "Literal source file represented by '${node.name}'",
            elementKind = GeneratedElementKind.StaticFile,
        )
    }

    override fun finalizeProject(
        document: ThreadworkDocument,
        projectName: String,
        files: List<GeneratedFile>,
        options: CompilerOptions,
    ): GeneratedProject {
        val rootPrefix = "${safeSegment(projectName)}/"
        val placedFiles = files.map { file ->
            val origin = file.originNodeId?.let(document::getElementById)
            val normalizedPath = file.path.removePrefix(rootPrefix)
            when {
                origin == null || origin.id == document.rootNodeId || origin.explicitFilePath() != null ->
                    file.copy(path = normalizedPath)

                else -> file.copy(
                    path = (parentDirectories(document, origin) + normalizedPath.substringAfterLast('/')).joinToString("/"),
                )
            }
        }
        return GeneratedProject(
            name = projectName,
            files = (placedFiles + literalFallbackFiles(document) + mermaidLinkFiles(document))
                .distinctBy { file -> file.path },
        )
    }

    private fun literalFallbackFiles(document: ThreadworkDocument): List<GeneratedFile> =
        document.nodes.values.mapNotNull { node ->
            val source = node.text.declaration.ifBlank { node.text.specification }
            val technologyId = document.effectiveTechnologyId(node.id).trim()
            val isForeignStandaloneFile =
                !node.isLink &&
                    node.children.isEmpty() &&
                    source.isNotBlank() &&
                    (
                        technologyId in setOf("filesystem", "file", "generic") ||
                            document.effectiveLayoutStrategyId(node.id) == SingleFileLayoutStrategy.id &&
                                technologyId !in setOf("c-native", "kotlin-jvm", "nodejs", "php")
                    )
            if (!isForeignStandaloneFile) return@mapNotNull null
            GeneratedFile(
                path = literalFilePath(document, node),
                content = source,
                originNodeId = node.id,
                reason = "Literal fallback file for technology '$technologyId'",
                elementKind = GeneratedElementKind.StaticFile,
            )
        }

    private fun mermaidLinkFiles(document: ThreadworkDocument): List<GeneratedFile> =
        document.nodes.values
            .asSequence()
            .filter(Node::isLink)
            .groupBy { link -> link.parentId ?: document.rootNodeId }
            .mapNotNull { (containerId, links) ->
                val container = document.getElementById(containerId) ?: return@mapNotNull null
                if (links.isEmpty()) return@mapNotNull null
                GeneratedFile(
                    path = mermaidPath(document, container),
                    content = mermaidDiagram(document, links),
                    originNodeId = container.id,
                    reason = "Mermaid topology for links owned by '${container.name}'",
                    elementKind = GeneratedElementKind.Link,
                )
            }
            .toList()

    private fun mermaidPath(document: ThreadworkDocument, container: Node): String {
        if (container.id == document.rootNodeId) return "links.mmd"
        val directories = parentDirectories(document, container)
        return if (document.effectiveLayoutStrategyId(container.id) == SingleFileLayoutStrategy.id) {
            (directories + "${safeSegment(container.name)}.links.mmd").joinToString("/")
        } else {
            (directories + safeSegment(container.name) + "links.mmd").joinToString("/")
        }
    }

    private fun mermaidDiagram(document: ThreadworkDocument, links: List<Node>): String = buildString {
        appendLine("flowchart LR")
        val endpoints = links
            .flatMap { link -> listOfNotNull(link.link?.sourceNodeId, link.link?.targetNodeId) }
            .distinct()
            .mapNotNull(document::getElementById)
        endpoints.forEach { node ->
            appendLine("    ${mermaidId(node.id)}[\"${mermaidText(node.name)}\"]")
        }
        links.forEach { linkNode ->
            val link = linkNode.link ?: return@forEach
            append("    ${mermaidId(link.sourceNodeId)} -->|\"")
            append(mermaidText(linkNode.name))
            link.typeName.takeIf { it.isNotBlank() }?.let { typeName ->
                append(": ")
                append(mermaidText(typeName))
            }
            appendLine("\"| ${mermaidId(link.targetNodeId)}")
        }
    }.trimEnd() + "\n"

    private fun literalFilePath(document: ThreadworkDocument, node: Node): String =
        node.explicitFilePath() ?: (parentDirectories(document, node) + node.name.removePrefix("@")).joinToString("/")

    private fun parentDirectories(document: ThreadworkDocument, node: Node): List<String> {
        val parents = mutableListOf<Node>()
        var parentId = node.parentId
        while (parentId != null && parentId != document.rootNodeId) {
            val parent = document.getElementById(parentId) ?: break
            parents += parent
            parentId = parent.parentId
        }
        return parents.asReversed()
            .filter { parent -> parent.children.isNotEmpty() && document.effectiveLayoutStrategyId(parent.id) != SingleFileLayoutStrategy.id }
            .map { parent -> safeSegment(parent.name) }
    }

    private fun Node.explicitFilePath(): String? =
        metadata["path"]?.takeIf { it.isNotBlank() }
            ?: metadata["file"]?.takeIf { it.isNotBlank() }

    private fun mermaidId(nodeId: NodeId): String =
        "node_${nodeId.value.replace(Regex("[^A-Za-z0-9_]"), "_")}"

    private fun mermaidText(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace('\n', ' ')

    private fun safeSegment(value: String): String =
        value.trim().replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_').ifBlank { "project" }
}
