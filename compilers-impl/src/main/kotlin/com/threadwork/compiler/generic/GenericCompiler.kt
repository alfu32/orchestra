package com.threadwork.compiler.generic

import com.threadwork.compiler.api.ANY_LANGUAGE_ID
import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.CompilationResult
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.NodeCompilerContext
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.projectName
import com.threadwork.core.validation.DocumentValidator

open class GenericCompiler : TemplateSetCompiler() {
    override val id: String = "generic-flow-design"
    override val displayName: String = "Generic Flow-Design Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("generic")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(ANY_LANGUAGE_ID, "generic"))

    override fun supports(document: ThreadworkDocument): Boolean =
        document.nodes.values.any { node ->
            node.stereotype(document) in setOf(NodeStereotype.StaticFile, NodeStereotype.CompilerTemplate)
        }

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun getStaticFiles(document: ThreadworkDocument, options: CompilerOptions): List<String> =
        document.nodes.values
            .filter { it.stereotype(document) == NodeStereotype.StaticFile }
            .flatMap { staticFilePath(it)?.let(::listOf) ?: staticFileList(it) }

    protected open fun templateOverrideFor(key: String): String? =
        null

    protected open fun projectFileOverrides(): List<TemplateGeneratedFile> =
        emptyList()

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet {
        val graphOverrides = compilerTemplateOverrides(document)
        val graphTemplates = graphOverrides.templates
        val generatedTemplates = CompilerTemplateRoles.all
            .associateWith { role -> templateOverrideFor(role).orEmpty() }
            .filterValues { it.isNotBlank() }
        val legacyGeneratedTemplates = legacyTemplateRoles
            .mapNotNull { (legacyKey, role) -> templateOverrideFor(legacyKey)?.let { role to it } }
            .toMap()
        val extension = document.getElementById(document.rootNodeId)?.technology?.fileExtension.orEmpty()
        return CompilerTemplateSet(
            templates = graphTemplates + legacyGeneratedTemplates + generatedTemplates,
            projectFiles = projectFileOverrides() + graphOverrides.projectFiles,
            staticFileNames = getStaticFiles(document, options).toSet(),
            fileExtension = extension,
            defaultLayoutStrategy = ClassifiedFilesystemLayoutStrategy,
        )
    }

    override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        context.node.isTemplateDefinition(context.document)

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val node = context.node
        if (node.stereotype(context.document) != NodeStereotype.StaticFile) return null
        return GeneratedFile(
            path = staticFilePath(node) ?: node.name.removePrefix("@").ifBlank { "static.txt" },
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Literal static file encoded in the flow design",
            elementKind = GeneratedElementKind.StaticFile,
        )
    }

}

class CompilerCompiler : CompilerPlugin {
    override val id: String = "compiler-compiler"
    override val displayName: String = "Compiler Compiler"
    override val supportedLanguageIds: Set<String> = setOf(ANY_LANGUAGE_ID)
    override val supportedTechnologyIds: Set<String> = setOf("compiler-compiler")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(ANY_LANGUAGE_ID, "compiler-compiler"))

    override fun supports(document: ThreadworkDocument): Boolean =
        compilerRoot(document) != null

    override fun validate(document: ThreadworkDocument): List<Diagnostic> {
        val diagnostics = DocumentValidator.validate(document).toMutableList()
        val compilerNodes = document.nodes.values.filter { it.name.equals("@Compiler", ignoreCase = true) }
        if (compilerNodes.size > 1) {
            diagnostics += Diagnostic(DiagnosticSeverity.Error, "CompilerCompiler expects exactly one @Compiler node.")
        }
        return diagnostics
    }

    override fun compile(document: ThreadworkDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document)
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }
        val root = compilerRoot(document)
            ?: return CompilationResult(null, diagnostics + Diagnostic(DiagnosticSeverity.Error, "No @Compiler node found."), success = false)
        val className = compilerClassName(root)
        val packageName = root.metadata["package"].orEmpty().ifBlank { "generated.compiler" }
        val technology = effectiveCompilerTechnology(document, root)
        val overrides = root.children.mapNotNull(document::getElementById)
            .filter { it.stereotype(document) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile) }
        val source = compilerClassSource(packageName, className, root, technology, overrides)
        val file = GeneratedFile(
            path = "src/main/kotlin/${packageName.replace('.', '/')}/$className.kt",
            content = source,
            originNodeId = root.id,
            reason = "Compiler class generated from @Compiler and child overrides",
            elementKind = GeneratedElementKind.CompilerTemplate,
        )
        return CompilationResult(
            generatedProject = ClassifiedFilesystemLayoutStrategy.layout(document, options.projectName ?: document.projectName(), listOf(file), options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    private fun compilerClassSource(
        packageName: String,
        className: String,
        root: Node,
        technology: TechnologyMetadata,
        overrideNodes: List<Node>,
    ): String {
        val projectFileNodes = overrideNodes.filter { it.isProjectFileTemplate() }
        val overrides = overrideNodes.filterNot { it.isProjectFileTemplate() }.associateBy(::overrideKey)
        val staticFiles = overrides[STATIC_FILES_OVERRIDE_KEY]
            ?.let(::staticFileListLiteral)
            ?: "emptyList()"
        val templateCases = overrides
            .filterKeys { it != STATIC_FILES_OVERRIDE_KEY }
            .entries
            .joinToString("\n") { (key, node) ->
                "            \"${key.escapeKotlinString()}\" -> ${templateText(node).kotlinTripleQuoted()}.trimIndent()"
            }
            .ifBlank { "            else -> null" }
        val templateWhen = if (templateCases.contains("else -> null")) {
            templateCases
        } else {
            "$templateCases\n            else -> null"
        }
        val projectFiles = projectFileNodes.joinToString(",\n") { node ->
            val pathTemplate = staticFilePath(node).orEmpty()
            "        TemplateGeneratedFile(${pathTemplate.kotlinTripleQuoted()}.trimIndent(), ${templateText(node).kotlinTripleQuoted()}.trimIndent())"
        }.let { entries ->
            if (entries.isBlank()) "emptyList()" else "listOf(\n$entries\n    )"
        }
        return """
package $packageName

import com.threadwork.compiler.api.ANY_LANGUAGE_ID
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.generic.GenericCompiler
import com.threadwork.compiler.generic.TemplateGeneratedFile
import com.threadwork.core.model.ThreadworkDocument

class $className : GenericCompiler() {
    override val id: String = "${safeCompilerId(root.name)}"
    override val displayName: String = "${root.name.removePrefix("@").ifBlank { className }}"
    override val supportedLanguageIds: Set<String> = setOf("${technology.languageId.ifBlank { ANY_LANGUAGE_ID }}")
    override val supportedTechnologyIds: Set<String> = setOf("${technology.technologyId.ifBlank { "generic" }}")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(supportedLanguageIds.first(), supportedTechnologyIds.first()))

    override fun supports(document: ThreadworkDocument): Boolean = true
    override fun validate(document: ThreadworkDocument) = emptyList<com.threadwork.core.diagnostics.Diagnostic>()

    override fun getStaticFiles(document: ThreadworkDocument, options: CompilerOptions): List<String> =
        $staticFiles

    override fun projectFileOverrides(): List<TemplateGeneratedFile> =
        $projectFiles

    override fun templateOverrideFor(key: String): String? =
        when (key) {
$templateWhen
        }
}
""".trimStart()
    }

    private fun staticFileListLiteral(node: Node): String {
        val paths = staticFileList(node)
        return if (paths.isEmpty()) {
            "emptyList()"
        } else {
            paths.joinToString(prefix = "listOf(", postfix = ")") { "\"${it.escapeKotlinString()}\"" }
        }
    }

    private fun compilerRoot(document: ThreadworkDocument): Node? =
        document.nodes.values.singleOrNull { it.name.equals("@Compiler", ignoreCase = true) }

    private fun compilerClassName(node: Node): String =
        node.metadata["className"]
            ?: node.metadata["class"]
            ?: "${node.name.removePrefix("@").toPascalCase().ifBlank { "Generated" }}Compiler"

    private fun effectiveCompilerTechnology(document: ThreadworkDocument, node: Node): TechnologyMetadata =
        node.technology.copy(
            languageId = document.effectiveLanguageId(node.id),
            technologyId = node.technology.technologyId.trim().ifBlank { safeCompilerId(document.projectName()) },
        )
}

internal data class CompilerTemplateOverrides(
    val templates: Map<String, String>,
    val projectFiles: List<TemplateGeneratedFile>,
)

/** Model-provided compiler templates shared by built-in and generated compilers. */
internal fun compilerTemplateOverrides(document: ThreadworkDocument): CompilerTemplateOverrides {
    val compilerTemplates = document.nodes.values
        .filter { it.stereotype(document) == NodeStereotype.CompilerTemplate }
    return CompilerTemplateOverrides(
        templates = compilerTemplates
            .filterNot(Node::isProjectFileTemplate)
            .associate { overrideKey(it) to templateText(it) },
        projectFiles = compilerTemplates
            .filter(Node::isProjectFileTemplate)
            .mapNotNull { node ->
                staticFilePath(node)?.let { path ->
                    TemplateGeneratedFile(path, templateText(node), "Project file template '${node.name}'")
                }
            },
    )
}

private fun Node.isTemplateDefinition(document: ThreadworkDocument): Boolean =
    stereotype(document) in setOf(NodeStereotype.CompilerTemplate, NodeStereotype.StaticFile)

private fun overrideKey(node: Node): String =
    node.name.trim().removePrefix("@").ifBlank { NodeKind.Node.name }.normalizeCompilerOverrideKey()

private fun String.normalizeCompilerOverrideKey(): String =
    normalizedOverrideName().let { normalized ->
        CompilerTemplateRoles.explicitOverrideNames[normalized]
            ?: when (normalized) {
                "staticfile" -> STATIC_FILES_OVERRIDE_KEY
                "processor", "processingunit" -> CompilerTemplateRoles.ProcessorDeclaration
                "node" -> CompilerTemplateRoles.NodeDeclaration
                "link" -> CompilerTemplateRoles.LinkDeclaration
                "group" -> CompilerTemplateRoles.GroupDeclaration
                "note", "compilertemplate" -> CompilerTemplateRoles.NoteDeclaration
                else -> NodeStereotype.entries
                    .firstOrNull { it.name.normalizedOverrideName() == normalized }
                    ?.let(CompilerTemplateRoles::stereotypeDeclaration)
                    ?: lowercase()
            }
    }

private fun String.normalizedOverrideName(): String =
    lowercase().replace(Regex("[^a-z0-9]+"), "")

private fun templateText(node: Node): String {
    val parts = listOf(node.text.instantiation, node.text.declaration).filter { it.isNotBlank() }
    return parts.joinToString("\n").ifBlank { node.text.specification }
}

private fun staticFilePath(node: Node): String? =
    node.metadata["path"]?.takeIf { it.isNotBlank() }
        ?: node.metadata["file"]?.takeIf { it.isNotBlank() }

private fun Node.isProjectFileTemplate(): Boolean =
    name.trim().equals("@ProjectFile", ignoreCase = true)

private fun staticFileList(node: Node): List<String> =
    templateText(node)
        .lines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }

private const val STATIC_FILES_OVERRIDE_KEY = "static-files"

private val legacyTemplateRoles: Map<String, String> = buildMap {
    put(NodeKind.Node.name, CompilerTemplateRoles.NodeDeclaration)
    put(NodeKind.Processor.name, CompilerTemplateRoles.ProcessorDeclaration)
    put(NodeKind.Link.name, CompilerTemplateRoles.LinkDeclaration)
    put(NodeKind.Group.name, CompilerTemplateRoles.GroupDeclaration)
    put(NodeKind.Type.name, CompilerTemplateRoles.TypeDeclaration)
    put(NodeKind.Note.name, CompilerTemplateRoles.NoteDeclaration)
    NodeStereotype.entries.forEach { stereotype ->
        put(stereotype.name, CompilerTemplateRoles.stereotypeDeclaration(stereotype))
    }
}

private fun safeCompilerId(value: String): String =
    value.removePrefix("@")
        .lowercase()
        .replace(Regex("[^a-z0-9_.-]+"), "-")
        .trim('-')
        .ifBlank { "generated-compiler" }

private fun String.toPascalCase(): String =
    split(Regex("[^A-Za-z0-9]+"))
        .filter { it.isNotBlank() }
        .joinToString("") { token -> token.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }

private fun String.kotlinTripleQuoted(): String =
    "\"\"\"\n${replace("\"\"\"", "\"\"\\\"").replace("$", "\${'$'}")}\n\"\"\""

private fun String.escapeKotlinString(): String =
    flatMap { char ->
        when (char) {
            '\\' -> "\\\\".toList()
            '"' -> "\\\"".toList()
            '\n' -> "\\n".toList()
            '\r' -> "\\r".toList()
            '\t' -> "\\t".toList()
            else -> listOf(char)
        }
    }.joinToString("")
