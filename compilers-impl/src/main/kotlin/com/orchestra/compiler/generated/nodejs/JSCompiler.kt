package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompiledNodeArtifact
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.LayoutStrategy
import com.orchestra.compiler.api.NodeCompilerContext
import com.orchestra.compiler.api.StructuredCompiler
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.getElementById
import com.orchestra.core.validation.DocumentValidator
import java.nio.file.Path

class JSCompiler : StructuredCompiler() {
    override val id: String = "nodejs-compiler"
    override val displayName: String = "Node.js CommonJS Compiler"
    override val supportedLanguageIds: Set<String> = setOf("javascript")
    override val supportedTechnologyIds: Set<String> = setOf("nodejs")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("javascript", "nodejs"))
    override val magicFileNames: Set<String> = setOf("package.json", "config.json")

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        DirectFileSystemHomorphismLayoutStrategy

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

    override fun fileExtension(context: NodeCompilerContext): String =
        "js"

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val node = context.node
        if (node.stereotype(context.document) != NodeStereotype.StaticFile && node.name !in magicFileNames) return null
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

    override fun getProcessorDeclaration(context: NodeCompilerContext): String =
        if (context.node.children.isNotEmpty()) compositeDeclaration(context) else processorDeclaration(context)

    override fun getNodeDeclaration(context: NodeCompilerContext): String =
        if (context.node.children.isNotEmpty()) compositeDeclaration(context) else processorDeclaration(context)

    override fun getGroupDeclaration(context: NodeCompilerContext): String =
        compositeDeclaration(context)

    override fun getNoteDeclaration(context: NodeCompilerContext): String =
        commentBlock(context.node.text.declaration.ifBlank { context.node.text.specification })

    override fun getLinkDeclaration(context: NodeCompilerContext): String {
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

    override fun getLinkInstantiation(context: NodeCompilerContext): String {
        val link = context.node.link ?: return ""
        val sourceNode = context.document.getElementById(link.sourceNodeId)
        val targetNode = context.document.getElementById(link.targetNodeId)
        val linkReference = context.node.name
        val sourceReference = "${sourceNode?.name ?: link.sourceNodeId.value}.${link.sourcePortName}"
        val targetReference = "${targetNode?.name ?: link.targetNodeId.value}.${link.targetPortName}"
        return "transport(context, \"${linkReference.escapeJs()}\", \"${sourceReference.escapeJs()}\", \"${targetReference.escapeJs()}\");"
    }

    override fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String {
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

${exportFunction(context, functionName)}
""".trimStart()
    }

    private fun compositeDeclaration(context: NodeCompilerContext): String {
        val functionName = safeFunctionName(context.node.name)
        val imports = if (context.isSingleFileLayout) "" else childImportsFor(context)
        val inlineChildren = if (context.isSingleFileLayout) context.childDeclarations else ""
        val links = context.linkDeclarations
        val childCalls = context.childArtifacts
            .filter { it.node.kind != NodeKind.Note }
            .joinToString("\n") { "${safeFunctionName(it.node.name)}(context);" }
        val linkCalls = context.linkArtifacts.joinToString("\n") { it.instantiationText }
        val ownDeclaration = context.node.text.declaration.trimEnd()
        val ownInstantiation = context.node.text.instantiation.trimEnd()
        return listOf(
            imports,
            runtimeSupport(),
            inlineChildren,
            links,
            """
function $functionName(context = {}) {
${ownInstantiation.indentJs()}
${ownDeclaration.indentJs()}
${childCalls.indentJs()}
${linkCalls.indentJs()}
}

${exportFunction(context, functionName)}
""".trimStart(),
        ).filter { it.isNotBlank() }.joinToString("\n\n")
    }

    private fun exportFunction(context: NodeCompilerContext, functionName: String): String =
        if (context.isSingleFileLayout) {
            "module.exports.$functionName = $functionName;"
        } else {
            "module.exports = { $functionName };"
        }

    private fun runtimeSupport(): String =
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
