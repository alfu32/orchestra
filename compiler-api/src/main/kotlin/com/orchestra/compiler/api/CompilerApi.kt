package com.orchestra.compiler.api

import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
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

interface CompilerPlugin {
    val id: String
    val displayName: String
    val supportedLanguageIds: Set<String> get() = emptySet()
    val supportedTechnologyIds: Set<String> get() = emptySet()
    val magicFileNames: Set<String> get() = emptySet()

    fun supports(document: InflowDocument): Boolean
    fun validate(document: InflowDocument): List<Diagnostic>

    fun entityStereotype(document: InflowDocument, node: Node): NodeStereotype =
        node.stereotype(document)

    fun linkStereotype(document: InflowDocument, linkNode: Node): LinkStereotype =
        LinkClassifier.classify(document, linkNode)

    fun generatedTextFor(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (entityStereotype(document, node)) {
            NodeStereotype.Node -> getNode(document, node, options)
            NodeStereotype.Link -> getLink(document, node, options)
            NodeStereotype.ProcessingUnit -> getProcessingUnit(document, node, options)
            NodeStereotype.CompositeWorker -> getCompositeWorker(document, node, options)
            NodeStereotype.CompositeErrorHandler -> getCompositeErrorHandler(document, node, options)
            NodeStereotype.Generator -> getGenerator(document, node, options)
            NodeStereotype.Transformer -> getTransformer(document, node, options)
            NodeStereotype.Sink -> getSink(document, node, options)
            NodeStereotype.Script -> getScript(document, node, options)
            NodeStereotype.ErrorHandler -> getErrorHandler(document, node, options)
            NodeStereotype.ServiceLibrary -> getServiceLibrary(document, node, options)
            NodeStereotype.Transport -> getTransport(document, node, options)
            NodeStereotype.ErrorPipe -> getErrorPipe(document, node, options)
            NodeStereotype.DependencyInjection -> getDependencyInjection(document, node, options)
            NodeStereotype.Test -> getTest(document, node, options)
            NodeStereotype.TestSuite -> getTestSuite(document, node, options)
            NodeStereotype.InputPort -> getInputPort(document, node, options)
            NodeStereotype.OutputPort -> getOutputPort(document, node, options)
            NodeStereotype.StaticFile -> getStaticFile(document, node, options)
            NodeStereotype.CompilerTemplate -> getCompilerTemplate(document, node, options)
        }

    fun getNode(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getLink(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getProcessingUnit(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getCompositeWorker(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getCompositeErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getGenerator(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getTransformer(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getSink(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getScript(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getServiceLibrary(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getTransport(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getErrorPipe(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getDependencyInjection(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getTest(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getTestSuite(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getInputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getOutputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
    fun getStaticFile(document: InflowDocument, node: Node, options: CompilerOptions): String = ""
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
