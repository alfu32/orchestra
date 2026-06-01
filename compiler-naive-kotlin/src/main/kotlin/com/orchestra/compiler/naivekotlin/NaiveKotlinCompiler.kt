package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.CompilationResult
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.GeneratedProject
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.validation.DocumentValidator

class NaiveKotlinCompiler : CompilerPlugin {
    override val id: String = "naive-kotlin"
    override val displayName: String = "Naive Kotlin/JVM Compiler"
    override val supportedLanguageIds: Set<String> = setOf("kotlin")
    override val supportedTechnologyIds: Set<String> = setOf("kotlin-jvm")

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun compile(document: InflowDocument, options: CompilerOptions): CompilationResult {
        val diagnostics = validate(document)
        if (diagnostics.any { it.severity == DiagnosticSeverity.Error }) {
            return CompilationResult(null, diagnostics, success = false)
        }

        val projectName = sanitizeIdentifier(options.projectName ?: document.name).ifBlank { "generated_project" }
        val names = FunctionNames(document)
        val files = mutableListOf<GeneratedFile>()
        files += settings(projectName)
        files += buildFile()
        files += runtimeFile()
        files += mainFile(document, names)

        document.nodes.values
            .filter { !it.isLink }
            .sortedBy { it.id.value }
            .forEach { node -> files += nodeFile(document, node, names) }

        return CompilationResult(
            generatedProject = GeneratedProject(projectName, files),
            diagnostics = diagnostics,
            success = true,
        )
    }

    private fun settings(projectName: String) = GeneratedFile(
        path = "settings.gradle.kts",
        content = """rootProject.name = "$projectName"
""",
        originNodeId = null,
        reason = "Gradle settings",
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
    )

    private fun mainFile(document: InflowDocument, names: FunctionNames) = GeneratedFile(
        path = "src/main/kotlin/generated/Main.kt",
        content = """
package generated

fun main() {
    val context = RuntimeContext()
    generated.nodes.${names.functionFor(document.rootNodeId)}(context)
}
""".trimStart(),
        originNodeId = document.rootNodeId,
        reason = "Application entry point",
    )

    private fun nodeFile(document: InflowDocument, node: Node, names: FunctionNames): GeneratedFile {
        val body = if (node.children.isEmpty()) terminalBody(node) else compositeBody(document, node, names)
        return GeneratedFile(
            path = "src/main/kotlin/generated/nodes/${names.classFileFor(node.id)}.kt",
            content = """
package generated.nodes

import generated.RuntimeContext
import generated.runLink

fun ${names.functionFor(node.id)}(context: RuntimeContext) {
$body
}
""".trimStart(),
            originNodeId = node.id,
            reason = if (node.children.isEmpty()) "Terminal processor node" else "Composite node runner",
        )
    }

    private fun terminalBody(node: Node): String {
        val source = node.text.source.trimEnd()
        return if (source.isBlank()) {
            "    // Node '${node.name}' has no source text yet.\n"
        } else {
            source.lines().joinToString(separator = "\n", postfix = "\n") { "    $it" }
        }
    }

    private fun compositeBody(document: InflowDocument, node: Node, names: FunctionNames): String {
        val children = node.children.mapNotNull(document.nodes::get)
        val processors = children.filter { !it.isLink }
        val links = children.filter { it.isLink }
        val calls = processors.joinToString(separator = "\n") {
            "    generated.nodes.${names.functionFor(it.id)}(context)"
        }
        val linkCalls = links.joinToString(separator = "\n") { linkNode ->
            val link = linkNode.link
            if (link == null) {
                "    // Link '${linkNode.name}' has no link data."
            } else {
                val source = "${sanitizeKey(document.nodes[link.sourceNodeId]?.name ?: link.sourceNodeId.value)}.${sanitizeKey(link.sourcePortName)}"
                val target = "${sanitizeKey(document.nodes[link.targetNodeId]?.name ?: link.targetNodeId.value)}.${sanitizeKey(link.targetPortName)}"
                "    runLink(context, \"$source\", \"$target\")"
            }
        }
        return listOf(calls, linkCalls).filter { it.isNotBlank() }.joinToString(separator = "\n", postfix = "\n")
            .ifBlank { "    // Composite node '${node.name}' has no executable children.\n" }
    }
}

private class FunctionNames(document: InflowDocument) {
    private val functionNames: Map<NodeId, String> = document.nodes.values
        .filter { !it.isLink }
        .sortedBy { it.id.value }
        .mapIndexed { index, node -> node.id to "run_${sanitizeIdentifier(node.name)}_${index + 1}" }
        .toMap()

    fun functionFor(id: NodeId): String = functionNames[id] ?: error("No function for node '$id'")

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
