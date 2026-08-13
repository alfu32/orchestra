package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompiledNodeArtifact
import com.orchestra.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.LayoutCompilerVariant
import com.orchestra.compiler.api.LayoutCompositeCompiler
import com.orchestra.compiler.api.NodeCompilerContext
import com.orchestra.compiler.api.SingleFileLayoutStrategy
import com.orchestra.compiler.api.SourceSetLayoutStrategy
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.getElementById
import com.orchestra.core.validation.DocumentValidator
import java.nio.file.Path

class JSCompiler : LayoutCompositeCompiler() {
    override val id: String = "nodejs-compiler"
    override val displayName: String = "Node.js CommonJS Compiler"
    override val supportedLanguageIds: Set<String> = setOf("javascript")
    override val supportedTechnologyIds: Set<String> = setOf("nodejs")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("javascript", "nodejs"))
    override val magicFileNames: Set<String> = setOf("package.json", "config.json")
    override val defaultLayoutStrategyId: String = DirectFileSystemHomorphismLayoutStrategy.id
    override val layoutVariants: List<LayoutCompilerVariant> = listOf(
        JSSingleFileCompiler(),
        JSDirectFileSystemCompiler(),
        JSClassifiedFileSystemCompiler(),
        JSSourceSetCompiler(),
    )

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

}

private abstract class JSLayoutCompilerVariant(
    final override val layoutStrategy: com.orchestra.compiler.api.LayoutStrategy,
) : LayoutCompilerVariant {
    override fun projectFiles(document: InflowDocument, options: CompilerOptions, projectName: String): List<GeneratedFile> =
        listOf(
            GeneratedFile(
                path = "${safeSegment(projectName)}/package.json",
                content = """
{
  "name": "${safePackageName(projectName)}",
  "version": "0.0.0",
  "type": "commonjs",
  "main": "${safeFunctionName(rootName(document))}.js"
}
""".trimStart(),
                originNodeId = null,
                reason = "Node.js package manifest",
                elementKind = GeneratedElementKind.ProjectLayout,
            ),
        )

    final override fun fileExtension(context: NodeCompilerContext): String =
        "js"

    final override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val node = context.node
        if (node.stereotype(context.document) != NodeStereotype.StaticFile && node.name !in JS_MAGIC_FILE_NAMES) return null
        val path = node.metadata["path"]?.takeIf { it.isNotBlank() }
            ?: node.metadata["file"]?.takeIf { it.isNotBlank() }
            ?: "${safeSegment(context.projectName)}/${node.name}"
        return GeneratedFile(
            path = path,
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Literal static file",
            elementKind = GeneratedElementKind.StaticFile,
        )
    }

    final override fun declarationFor(context: NodeCompilerContext): String =
        when (context.node.kind) {
            NodeKind.Link -> linkDeclaration(context)
            NodeKind.Note -> commentBlock(context.node.text.declaration.ifBlank { context.node.text.specification })
            NodeKind.Group -> compositeDeclaration(context)
            NodeKind.Node, NodeKind.Processor ->
                if (context.node.children.isNotEmpty()) compositeDeclaration(context) else processorDeclaration(context)
        }

    final override fun instantiationFor(context: NodeCompilerContext): String =
        if (context.node.kind == NodeKind.Link) linkInstantiation(context) else context.node.text.instantiation

    private fun linkDeclaration(context: NodeCompilerContext): String {
        val link = context.node.link
        val typeName = link?.typeName?.takeIf { it.isNotBlank() } ?: context.node.name
        val definition = link?.payloadDefinition?.ifBlank { context.node.text.declaration } ?: context.node.text.declaration
        return listOf(
            "/**",
            " * Link ${context.node.name}:${typeName}",
            definition.lines().joinToString("\n") { " * $it" },
            " */",
        ).joinToString("\n")
    }

    private fun linkInstantiation(context: NodeCompilerContext): String {
        val link = context.node.link ?: return ""
        val sourceNode = context.document.getElementById(link.sourceNodeId)
        val targetNode = context.document.getElementById(link.targetNodeId)
        val linkReference = context.node.name
        val sourceReference = "${sourceNode?.name ?: link.sourceNodeId.value}.${link.sourcePortName}"
        val targetReference = "${targetNode?.name ?: link.targetNodeId.value}.${link.targetPortName}"
        return "transport(context, \"${linkReference.escapeJs()}\", \"${sourceReference.escapeJs()}\", \"${targetReference.escapeJs()}\");"
    }

    final override fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String {
        val childFile = child.primaryFile ?: return ""
        val currentFile = context.primaryPath()
        val relative = relativeRequire(currentFile, childFile.path)
        val functionName = safeFunctionName(child.node.name)
        return "const { $functionName } = require(\"$relative\");"
    }

    private fun processorDeclaration(context: NodeCompilerContext): String {
        val functionName = safeFunctionName(context.node.name)
        val body = context.node.text.declaration.trimEnd().ifBlank {
            "// ${context.node.name} has no declaration text yet."
        }
        val initialization = context.node.text.instantiation.trimEnd()
        return """
function $functionName(context = {}) {
${initialization.indentJs()}
${body.indentJs()}
}

${exportFunction(functionName)}
""".trimStart()
    }

    protected abstract fun compositeDeclaration(context: NodeCompilerContext): String

    protected fun compositeFunction(context: NodeCompilerContext): String {
        val functionName = safeFunctionName(context.node.name)
        val childCalls = context.childArtifacts
            .filter { it.node.kind != NodeKind.Note }
            .joinToString("\n") { "${safeFunctionName(it.node.name)}(context);" }
        val linkCalls = context.linkArtifacts.joinToString("\n") { it.instantiationText }
        val ownDeclaration = context.node.text.declaration.trimEnd()
        val ownInstantiation = context.node.text.instantiation.trimEnd()
        return """
function $functionName(context = {}) {
${ownInstantiation.indentJs()}
${ownDeclaration.indentJs()}
${childCalls.indentJs()}
${linkCalls.indentJs()}
}

${exportFunction(functionName)}
""".trimStart()
    }

    protected fun childImports(context: NodeCompilerContext): String =
        context.childArtifacts.joinToString("\n") { importForChild(context, it) }.trim()

    protected fun exportFunction(functionName: String): String =
        "module.exports.$functionName = $functionName;"

    protected fun runtimeSupport(): String =
        """
function transport(context, linkReference, source, target) {
  context.outputs = context.outputs || {};
  context.inputs = context.inputs || {};
  context.links = context.links || {};
  context.links[linkReference] = { source, target };
  const queue = context.outputs[source] || [];
  context.inputs[target] = context.inputs[target] || [];
  while (queue.length > 0) context.inputs[target].push(queue.shift());
}
""".trimStart()
}

private class JSSingleFileCompiler : JSLayoutCompilerVariant(SingleFileLayoutStrategy) {
    override fun compositeDeclaration(context: NodeCompilerContext): String =
        listOf(
            context.externalChildArtifacts.joinToString("\n") { importForChild(context, it) },
            runtimeSupport(),
            context.inlineChildDeclarations,
            context.linkDeclarations,
            compositeFunction(context),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
}

private abstract class JSFilePerNodeCompiler(
    strategy: com.orchestra.compiler.api.LayoutStrategy,
) : JSLayoutCompilerVariant(strategy) {
    override fun compositeDeclaration(context: NodeCompilerContext): String =
        listOf(
            childImports(context),
            runtimeSupport(),
            context.linkDeclarations,
            compositeFunction(context),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
}

private class JSDirectFileSystemCompiler : JSFilePerNodeCompiler(DirectFileSystemHomorphismLayoutStrategy)

private class JSClassifiedFileSystemCompiler : JSFilePerNodeCompiler(ClassifiedFilesystemLayoutStrategy)

private class JSSourceSetCompiler : JSFilePerNodeCompiler(SourceSetLayoutStrategy)

private val JS_MAGIC_FILE_NAMES = setOf("package.json", "config.json")

private fun rootName(document: InflowDocument): String =
    document.getElementById(document.rootNodeId)?.name ?: document.name.ifBlank { "main" }

private fun relativeRequire(from: String, to: String): String {
    val fromParent = Path.of(from).parent ?: Path.of("")
    val rel = fromParent.relativize(Path.of(to)).toString().replace('\\', '/').removeSuffix(".js")
    return if (rel.startsWith(".")) rel else "./$rel"
}

private fun String.indentJs(): String =
    trimEnd().lines().filter { it.isNotBlank() }.joinToString("\n") { "  $it" }

private fun commentBlock(value: String): String =
    value.lines().joinToString(prefix = "/**\n", postfix = "\n */") { " * $it" }

private fun safeFunctionName(value: String): String {
    val sanitized = value.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_').ifBlank { "node" }
    return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
}

private fun safeSegment(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_').ifBlank { "project" }

private fun safePackageName(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9_.-]+"), "-").trim('-').ifBlank { "project" }

private fun String.escapeJs(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")
