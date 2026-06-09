package com.orchestra.compiler.php

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

class PhpCompiler : StructuredCompiler() {
    override val id: String = "php-compiler"
    override val displayName: String = "PHP Compiler"
    override val supportedLanguageIds: Set<String> = setOf("php")
    override val supportedTechnologyIds: Set<String> = setOf("php")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("php", "php"))
    override val magicFileNames: Set<String> = setOf("composer.json", ".env")

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun layoutStrategy(options: CompilerOptions): LayoutStrategy =
        DirectFileSystemHomorphismLayoutStrategy

    override fun fileExtension(context: NodeCompilerContext): String =
        "php"

    override fun projectFiles(document: InflowDocument, options: CompilerOptions, projectName: String): List<GeneratedFile> =
        listOf(
            GeneratedFile(
                path = "${safeSegment(projectName)}/composer.json",
                content = """
{
  "name": "orchestra/${safePackageName(projectName)}",
  "type": "project",
  "require": {}
}
""".trimStart(),
                originNodeId = null,
                reason = "Composer manifest",
                elementKind = GeneratedElementKind.ProjectLayout,
            ),
        )

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
        phpComment(context.node.text.declaration.ifBlank { context.node.text.specification })

    override fun getLinkDeclaration(context: NodeCompilerContext): String {
        val link = context.node.link
        val typeName = link?.typeName?.takeIf { it.isNotBlank() } ?: context.node.name
        val definition = link?.payloadDefinition?.ifBlank { context.node.text.declaration } ?: context.node.text.declaration
        return phpComment("Link ${context.node.name}:$typeName\n$definition")
    }

    override fun getLinkInstantiation(context: NodeCompilerContext): String {
        val link = context.node.link ?: return ""
        val sourceNode = context.document.getElementById(link.sourceNodeId)
        val targetNode = context.document.getElementById(link.targetNodeId)
        val linkReference = context.node.name
        val sourceReference = "${sourceNode?.name ?: link.sourceNodeId.value}.${link.sourcePortName}"
        val targetReference = "${targetNode?.name ?: link.targetNodeId.value}.${link.targetPortName}"
        return "transport(\$context, '${linkReference.escapePhp()}', '${sourceReference.escapePhp()}', '${targetReference.escapePhp()}');"
    }

    override fun importForChild(context: NodeCompilerContext, child: CompiledNodeArtifact): String {
        val childFile = child.primaryFile ?: return ""
        val relative = relativePath(context.primaryPath(), childFile.path)
        return "require_once __DIR__ . '/$relative';"
    }

    private fun processorDeclaration(context: NodeCompilerContext): String {
        val functionName = safeFunctionName(context.node.name)
        val initialization = context.node.text.instantiation.trimEnd()
        val body = context.node.text.declaration.trimEnd().ifBlank {
            "// ${context.node.name} has no declaration text yet."
        }
        return """
<?php

function $functionName(array &${'$'}context = []): void
{
${initialization.indentPhp()}
${body.indentPhp()}
}
""".trimStart()
    }

    private fun compositeDeclaration(context: NodeCompilerContext): String {
        val functionName = safeFunctionName(context.node.name)
        val imports = if (context.isSingleFileLayout) "" else childImportsFor(context)
        val inlineChildren = if (context.isSingleFileLayout) context.childDeclarations.replace("<?php", "").trim() else ""
        val links = context.linkDeclarations
        val childCalls = context.childArtifacts
            .filter { it.node.kind != NodeKind.Note }
            .joinToString("\n") { "${safeFunctionName(it.node.name)}(\$context);" }
        val linkCalls = context.linkArtifacts.joinToString("\n") { it.instantiationText }
        val ownDeclaration = context.node.text.declaration.trimEnd()
        val ownInstantiation = context.node.text.instantiation.trimEnd()
        return """
<?php

$imports

${runtimeSupport()}

$inlineChildren

$links

function $functionName(array &${'$'}context = []): void
{
${ownInstantiation.indentPhp()}
${ownDeclaration.indentPhp()}
${childCalls.indentPhp()}
${linkCalls.indentPhp()}
}
""".trimStart()
    }

    private fun runtimeSupport(): String =
        """
function transport(array &${'$'}context, string ${'$'}linkReference, string ${'$'}source, string ${'$'}target): void
{
    ${'$'}context['outputs'] ??= [];
    ${'$'}context['inputs'] ??= [];
    ${'$'}context['links'] ??= [];
    ${'$'}context['links'][${'$'}linkReference] = ['source' => ${'$'}source, 'target' => ${'$'}target];
    ${'$'}queue = ${'$'}context['outputs'][${'$'}source] ?? [];
    ${'$'}context['inputs'][${'$'}target] ??= [];
    while (count(${'$'}queue) > 0) {
        ${'$'}context['inputs'][${'$'}target][] = array_shift(${'$'}queue);
    }
}
""".trimStart()
}

private fun relativePath(from: String, to: String): String {
    val fromParent = Path.of(from).parent ?: Path.of("")
    return fromParent.relativize(Path.of(to)).toString().replace('\\', '/')
}

private fun String.indentPhp(): String =
    trimEnd().lines().filter { it.isNotBlank() }.joinToString("\n") { "    $it" }

private fun phpComment(value: String): String =
    value.lines().joinToString(prefix = "/**\n", postfix = "\n */") { " * $it" }

private fun safeFunctionName(value: String): String {
    val sanitized = value.trim().replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_').ifBlank { "node" }
    return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
}

private fun safeSegment(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9_.-]+"), "_").trim('_').ifBlank { "project" }

private fun safePackageName(value: String): String =
    value.lowercase().replace(Regex("[^a-z0-9_.-]+"), "-").trim('-').ifBlank { "project" }

private fun String.escapePhp(): String =
    replace("\\", "\\\\").replace("'", "\\'")
