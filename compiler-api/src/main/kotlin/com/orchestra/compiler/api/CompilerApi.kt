package com.orchestra.compiler.api

import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
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

    @Deprecated("Use generatedDeclarationFor")
    fun generatedTextFor(document: InflowDocument, node: Node, options: CompilerOptions): String =
        generatedDeclarationFor(document, node, options)

    fun getNodeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNode(document, node, options)

    fun getNodeInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNodeDeclaration(document, node, options)

    fun getProcessorDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getProcessingUnit(document, node, options)

    fun getProcessorInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getProcessorDeclaration(document, node, options)

    fun getLinkDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getLink(document, node, options)

    fun getLinkInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getLinkDeclaration(document, node, options)

    fun getGroupDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getCompositeWorker(document, node, options)

    fun getGroupInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getGroupDeclaration(document, node, options)

    fun getNoteDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getScript(document, node, options)

    fun getNoteInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNoteDeclaration(document, node, options)

    @Deprecated("Use getNodeDeclaration")
    fun getNode(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getLinkDeclaration")
    fun getLink(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getProcessingUnit(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getGroupDeclaration")
    fun getCompositeWorker(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getGroupDeclaration")
    fun getCompositeErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getGenerator(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getTransformer(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getSink(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getNoteDeclaration")
    fun getScript(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getNodeDeclaration")
    fun getServiceLibrary(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getLinkDeclaration")
    fun getTransport(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getLinkDeclaration")
    fun getErrorPipe(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getDependencyInjection(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getProcessorDeclaration")
    fun getTest(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getGroupDeclaration")
    fun getTestSuite(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getLinkDeclaration")
    fun getInputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getLinkDeclaration")
    fun getOutputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getNodeDeclaration")
    fun getStaticFile(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    @Deprecated("Use getNoteDeclaration")
    fun getCompilerTemplate(document: InflowDocument, node: Node, options: CompilerOptions): String = ""

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

    fun layoutStrategy(options: CompilerOptions): GeneratedProjectLayoutStrategy =
        PreserveGeneratedPathsLayoutStrategy

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

interface GeneratedProjectLayoutStrategy {
    val id: String
    val displayName: String

    fun layout(projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject
}

object PreserveGeneratedPathsLayoutStrategy : GeneratedProjectLayoutStrategy {
    override val id: String = "preserve-generated-paths"
    override val displayName: String = "Preserve generated paths"

    override fun layout(projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
        GeneratedProject(projectName, files)
}

object SourceSetLayoutStrategy : GeneratedProjectLayoutStrategy {
    override val id: String = "source-set"
    override val displayName: String = "Source set layout"

    override fun layout(projectName: String, files: List<GeneratedFile>, options: CompilerOptions): GeneratedProject =
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
