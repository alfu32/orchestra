package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.NodeCompilerContext
import com.orchestra.compiler.api.StructuredCompiler
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.getElementById
import com.orchestra.core.validation.DocumentValidator

class NaiveKotlinCompiler : StructuredCompiler() {
    override val id: String = "naive-kotlin"
    override val displayName: String = "Naive Kotlin/JVM Compiler"
    override val supportedLanguageIds: Set<String> = setOf("kotlin")
    override val supportedTechnologyIds: Set<String> = setOf("kotlin-jvm")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("kotlin", "kotlin-jvm"))
    override val magicFileNames: Set<String> = setOf("build.gradle.kts", "settings.gradle.kts", "gradle.properties")

    private var names: FunctionNames = FunctionNames(emptyList())

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun beforeCompile(document: InflowDocument, options: CompilerOptions) {
        names = FunctionNames(document.nodes.values.filterNot { it.isLink }.sortedBy { it.id.value })
    }

    override fun afterCompile(document: InflowDocument, options: CompilerOptions) {
        names = FunctionNames(emptyList())
    }

    override fun normalizedProjectName(document: InflowDocument, options: CompilerOptions): String =
        sanitizeIdentifier(options.projectName ?: document.name).ifBlank { "generated_project" }

    override fun projectFiles(document: InflowDocument, options: CompilerOptions, projectName: String): List<GeneratedFile> {
        val scopeIds = compileScopeIds(document, options.scopeNodeIds)
        val executableIds = executableScopeRoots(document, scopeIds)
        return listOf(
            settings(projectName),
            buildFile(),
            runtimeFile(),
            mainFile(document, executableIds),
        )
    }

    override fun fileExtension(context: NodeCompilerContext): String =
        "kt"

    override fun shouldSkipNode(context: NodeCompilerContext): Boolean =
        context.node.stereotype(context.document) == NodeStereotype.CompilerTemplate

    override fun staticFileFor(context: NodeCompilerContext): GeneratedFile? {
        val node = context.node
        val path = node.metadata["path"]?.takeIf { it.isNotBlank() }
            ?: node.metadata["file"]?.takeIf { it.isNotBlank() }
            ?: node.name.takeIf { it in magicFileNames }
            ?: return null
        return GeneratedFile(
            path = path,
            content = node.text.declaration.ifBlank { node.text.specification },
            originNodeId = node.id,
            reason = "Magic project file",
            elementKind = GeneratedElementKind.MagicFile,
        )
    }

    override fun primaryFileFor(context: NodeCompilerContext, declaration: String): GeneratedFile? {
        if (declaration.isBlank() || context.node.isLink) return null
        return GeneratedFile(
            path = "src/main/kotlin/generated/nodes/${names.classFileFor(context.node.id)}.kt",
            content = declaration.trimEnd(),
            originNodeId = context.node.id,
            reason = if (context.node.children.isEmpty()) "Terminal processor node" else "Composite node runner",
            elementKind = if (context.node.children.isEmpty()) GeneratedElementKind.TerminalEntity else GeneratedElementKind.CompositeEntity,
        )
    }

    override fun getProcessorDeclaration(context: NodeCompilerContext): String =
        nodeFile(context)

    override fun getNodeDeclaration(context: NodeCompilerContext): String =
        nodeFile(context)

    override fun getGroupDeclaration(context: NodeCompilerContext): String =
        nodeFile(context)

    override fun getNoteDeclaration(context: NodeCompilerContext): String =
        metadataComment(context.document, context.node)

    override fun getLinkDeclaration(context: NodeCompilerContext): String =
        metadataComment(context.document, context.node) + "linkStereotype=${linkStereotype(context.document, context.node)}\n"

    override fun getLinkInstantiation(context: NodeCompilerContext): String {
        val link = context.node.link ?: return "    // Link '${context.node.name}' has no link data."
        val source = "${sanitizeKey(context.document.getElementById(link.sourceNodeId)?.name ?: link.sourceNodeId.value)}.${sanitizeKey(link.sourcePortName)}"
        val target = "${sanitizeKey(context.document.getElementById(link.targetNodeId)?.name ?: link.targetNodeId.value)}.${sanitizeKey(link.targetPortName)}"
        return "runLink(context, \"$source\", \"$target\")"
    }

    private fun nodeFile(context: NodeCompilerContext): String {
        val body = if (context.node.children.isEmpty()) terminalBody(context.node) else compositeBody(context)
        return """
package generated.nodes

import generated.RuntimeContext
import generated.runLink

fun ${names.initializerFor(context.node.id)}(context: RuntimeContext) {
${if (context.node.children.isEmpty()) terminalInitializationBody(context.node) else compositeInitializationBody(context)}
}

fun ${names.functionFor(context.node.id)}(context: RuntimeContext) {
$body
}
""".trimStart()
    }

    private fun terminalInitializationBody(node: Node): String {
        val source = node.text.instantiation.trimEnd()
        return if (source.isBlank()) {
            "    // Node '${node.name}' has no instantiation text yet.\n"
        } else {
            source.lines().joinToString(separator = "\n", postfix = "\n") { "    $it" }
        }
    }

    private fun terminalBody(node: Node): String {
        val source = node.text.declaration.trimEnd()
        return if (source.isBlank()) {
            "    // Node '${node.name}' has no declaration text yet.\n"
        } else {
            source.lines().joinToString(separator = "\n", postfix = "\n") { "    $it" }
        }
    }

    private fun compositeInitializationBody(context: NodeCompilerContext): String =
        context.childArtifacts.joinToString(separator = "\n", postfix = "\n") {
            "    generated.nodes.${names.initializerFor(it.node.id)}(context)"
        }.ifBlank { "    // Composite node '${context.node.name}' has no executable children to initialize.\n" }

    private fun compositeBody(context: NodeCompilerContext): String {
        val calls = context.childArtifacts.joinToString(separator = "\n") {
            "    generated.nodes.${names.functionFor(it.node.id)}(context)"
        }
        val linkCalls = context.linkArtifacts.joinToString(separator = "\n") { artifact ->
            artifact.instantiationText.lines().joinToString("\n") { "    $it" }
        }
        return listOf(calls, linkCalls).filter { it.isNotBlank() }.joinToString(separator = "\n", postfix = "\n")
            .ifBlank { "    // Composite node '${context.node.name}' has no executable children.\n" }
    }

    private fun settings(projectName: String) = GeneratedFile(
        path = "settings.gradle.kts",
        content = """rootProject.name = "$projectName"
""",
        originNodeId = null,
        reason = "Gradle settings",
        elementKind = GeneratedElementKind.ProjectLayout,
    )

    private fun buildFile() = GeneratedFile(
        path = "build.gradle.kts",
        content = """
plugins {
    kotlin("jvm") version "2.2.0"
    application
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("generated.MainKt")
}
""".trimStart(),
        originNodeId = null,
        reason = "Gradle build file",
        elementKind = GeneratedElementKind.ProjectLayout,
    )

    private fun runtimeFile() = GeneratedFile(
        path = "src/main/kotlin/generated/Runtime.kt",
        content = """
package generated

class RuntimeContext {
    val inputs: MutableMap<String, MutableList<Any?>> = mutableMapOf()
    val outputs: MutableMap<String, MutableList<Any?>> = mutableMapOf()
    val imports: MutableMap<String, Any?> = mutableMapOf()
}

fun runLink(context: RuntimeContext, source: String, target: String) {
    val sourceQueue = context.outputs.getOrPut(source) { mutableListOf() }
    val targetQueue = context.inputs.getOrPut(target) { mutableListOf() }

    while (sourceQueue.isNotEmpty()) {
        targetQueue.add(sourceQueue.removeAt(0))
    }
}
""".trimStart(),
        originNodeId = null,
        reason = "Runtime support",
        elementKind = GeneratedElementKind.Runtime,
    )

    private fun mainFile(document: InflowDocument, executableIds: List<NodeId>) = GeneratedFile(
        path = "src/main/kotlin/generated/Main.kt",
        content = """
package generated

fun main() {
    val context = RuntimeContext()
${executableIds.joinToString(separator = "\n") { "    generated.nodes.${names.initializerFor(it)}(context)" }}
${executableIds.joinToString(separator = "\n") { "    generated.nodes.${names.functionFor(it)}(context)" }}
}
""".trimStart(),
        originNodeId = document.rootNodeId,
        reason = "Application entry point",
        elementKind = GeneratedElementKind.ProjectLayout,
    )

    private fun metadataComment(document: InflowDocument, node: Node): String =
        "name=${node.name}\nkind=${node.kind.name}\nstereotype=${node.stereotype(document)}\n"
}

private fun compileScopeIds(document: InflowDocument, requested: Set<NodeId>): Set<NodeId> {
    if (requested.isEmpty()) return document.nodes.keys
    val result = linkedSetOf<NodeId>()
    fun include(id: NodeId) {
        val node = document.getElementById(id) ?: return
        if (result.add(id) && !node.isLink) node.children.forEach(::include)
    }
    requested.forEach(::include)
    val selectedNodes = result.mapNotNull(document::getElementById).filterNot { it.isLink }.map { it.id }.toSet()
    document.nodes.values.filter { it.isLink }.forEach { linkNode ->
        val link = linkNode.link ?: return@forEach
        if (link.sourceNodeId in selectedNodes && link.targetNodeId in selectedNodes) result += linkNode.id
    }
    return result
}

private fun executableScopeRoots(document: InflowDocument, scopeIds: Set<NodeId>): List<NodeId> {
    if (scopeIds.contains(document.rootNodeId)) return listOf(document.rootNodeId)
    val nodes = scopeIds.mapNotNull(document::getElementById).filterNot { it.isLink }
    return nodes
        .filter { node -> node.parentId !in scopeIds }
        .map { it.id }
        .ifEmpty { listOf(document.rootNodeId) }
}

private class FunctionNames(nodes: List<Node>) {
    private val functionNames: Map<NodeId, String> = nodes
        .mapIndexed { index, node -> node.id to "run_${sanitizeIdentifier(node.name)}_${index + 1}" }
        .toMap()

    fun functionFor(id: NodeId): String = functionNames[id] ?: error("No function for node '$id'")

    fun initializerFor(id: NodeId): String = functionFor(id).replaceFirst("run_", "init_")

    fun classFileFor(id: NodeId): String = functionFor(id).removePrefix("run_").replaceFirstChar {
        if (it.isLowerCase()) it.titlecase() else it.toString()
    }
}

private fun sanitizeIdentifier(value: String): String {
    val sanitized = value.trim()
        .replace(Regex("[^A-Za-z0-9_]+"), "_")
        .trim('_')
        .lowercase()
    val fallback = sanitized.ifBlank { "node" }
    return if (fallback.first().isDigit()) "_$fallback" else fallback
}

private fun sanitizeKey(value: String): String =
    value.trim().replace("\"", "\\\"")
