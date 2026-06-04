package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.CompilationResult
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.api.GeneratedFile
import com.orchestra.compiler.api.GeneratedProject
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.classification.stereotype
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.validation.DocumentValidator
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.getElementsByIds

class NaiveKotlinCompiler : GenericCompiler() {
    override val id: String = "naive-kotlin"
    override val displayName: String = "Naive Kotlin/JVM Compiler"
    override val supportedLanguageIds: Set<String> = setOf("kotlin")
    override val supportedTechnologyIds: Set<String> = setOf("kotlin-jvm")
    override val magicFileNames: Set<String> = setOf(
        "build.gradle.kts",
        "settings.gradle.kts",
        "gradle.properties",
    )

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
        val scopeIds = compileScopeIds(document, options.scopeNodeIds)
        val executableIds = executableScopeRoots(document, scopeIds)
        val files = mutableListOf<GeneratedFile>()
        files += settings(projectName)
        files += buildFile()
        files += runtimeFile()
        files += mainFile(document, names, executableIds)

        document.nodes.values
            .filter { !it.isLink && it.id in scopeIds }
            .sortedBy { it.id.value }
            .forEach { node ->
                generateMagicFile(document, node, options)?.let { files += it }
                files += if (node.children.isEmpty()) {
                    generateTerminalEntity(document, node, options)
                } else {
                    generateCompositeEntity(document, node, options)
                }
                files += nodeFile(document, node, names)
            }

        document.nodes.values
            .filter { it.isLink && it.id in scopeIds }
            .sortedBy { it.id.value }
            .forEach { linkNode -> files += generateLink(document, linkNode, options) }

        return CompilationResult(
            generatedProject = layoutStrategy(options).layout(document, projectName, files.distinctBy { it.path }, options),
            diagnostics = diagnostics,
            success = true,
        )
    }

    override fun generateTerminalEntity(document: InflowDocument, node: Node, options: CompilerOptions): List<GeneratedFile> =
        listOf(
            GeneratedFile(
                path = "src/main/kotlin/generated/metadata/${sanitizeIdentifier(node.name)}_${node.id.value.takeLast(8)}.entity.txt",
                content = generatedDeclarationFor(document, node, options),
                originNodeId = node.id,
                reason = "Terminal entity generation metadata",
                elementKind = GeneratedElementKind.TerminalEntity,
            ),
        )

    override fun generateCompositeEntity(document: InflowDocument, node: Node, options: CompilerOptions): List<GeneratedFile> =
        listOf(
            GeneratedFile(
                path = "src/main/kotlin/generated/metadata/${sanitizeIdentifier(node.name)}_${node.id.value.takeLast(8)}.composite.txt",
                content = generatedDeclarationFor(document, node, options) + "children=${node.children.joinToString(",")}\n",
                originNodeId = node.id,
                reason = "Composite entity generation metadata",
                elementKind = GeneratedElementKind.CompositeEntity,
            ),
        )

    override fun generateLink(document: InflowDocument, linkNode: Node, options: CompilerOptions): List<GeneratedFile> =
        listOf(
            GeneratedFile(
                path = "src/main/kotlin/generated/metadata/${sanitizeIdentifier(linkNode.name)}_${linkNode.id.value.takeLast(8)}.link.txt",
                content = generatedDeclarationFor(document, linkNode, options) + "linkStereotype=${linkStereotype(document, linkNode)}\n",
                originNodeId = linkNode.id,
                reason = "Link generation metadata",
                elementKind = GeneratedElementKind.Link,
            ),
        )

    override fun generateMagicFile(document: InflowDocument, node: Node, options: CompilerOptions): GeneratedFile? {
        val name = node.name.trim()
        if (name !in getStaticFiles(document, options)) return null
        val content = node.text.declaration.ifBlank { node.text.specification }
        return GeneratedFile(
            path = name,
            content = content,
            originNodeId = node.id,
            reason = "Magic project file",
            elementKind = GeneratedElementKind.MagicFile,
        )
    }

    override fun getNodeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Declaration")

    override fun getNodeInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Instantiation")

    override fun getProcessorDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Declaration")

    override fun getProcessorInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Instantiation")

    override fun getLinkDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Declaration")

    override fun getLinkInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Instantiation")

    override fun getGroupDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Declaration")

    override fun getGroupInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Instantiation")

    override fun getNoteDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Declaration")

    override fun getNoteInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        kindText(document, node, "Instantiation")

    private fun kindText(document: InflowDocument, node: Node, mode: String): String =
        "name=${node.name}\nkind=${node.kind.name}\nstereotype=${node.stereotype(document)}\ncompilerMethod=get${node.kind.name}$mode\n"

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

    private fun mainFile(document: InflowDocument, names: FunctionNames, executableIds: List<NodeId>) = GeneratedFile(
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

    private fun nodeFile(document: InflowDocument, node: Node, names: FunctionNames): GeneratedFile {
        val body = if (node.children.isEmpty()) terminalBody(node) else compositeBody(document, node, names)
        return GeneratedFile(
            path = "src/main/kotlin/generated/nodes/${names.classFileFor(node.id)}.kt",
            content = """
package generated.nodes

import generated.RuntimeContext
import generated.runLink

fun ${names.initializerFor(node.id)}(context: RuntimeContext) {
${if (node.children.isEmpty()) terminalInitializationBody(node) else compositeInitializationBody(document, node, names)}
}

fun ${names.functionFor(node.id)}(context: RuntimeContext) {
$body
}
""".trimStart(),
            originNodeId = node.id,
            reason = if (node.children.isEmpty()) "Terminal processor node" else "Composite node runner",
            elementKind = if (node.children.isEmpty()) GeneratedElementKind.TerminalEntity else GeneratedElementKind.CompositeEntity,
        )
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

    private fun compositeInitializationBody(document: InflowDocument, node: Node, names: FunctionNames): String {
        val processors = document.getElementsByIds(node.children).values.filter { !it.isLink }
        return processors.joinToString(separator = "\n", postfix = "\n") {
            "    generated.nodes.${names.initializerFor(it.id)}(context)"
        }.ifBlank { "    // Composite node '${node.name}' has no executable children to initialize.\n" }
    }

    private fun compositeBody(document: InflowDocument, node: Node, names: FunctionNames): String {
        val children = document.getElementsByIds(node.children).values.toList()
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
                val source = "${sanitizeKey(document.getElementById(link.sourceNodeId)?.name ?: link.sourceNodeId.value)}.${sanitizeKey(link.sourcePortName)}"
                val target = "${sanitizeKey(document.getElementById(link.targetNodeId)?.name ?: link.targetNodeId.value)}.${sanitizeKey(link.targetPortName)}"
                "    runLink(context, \"$source\", \"$target\")"
            }
        }
        return listOf(calls, linkCalls).filter { it.isNotBlank() }.joinToString(separator = "\n", postfix = "\n")
            .ifBlank { "    // Composite node '${node.name}' has no executable children.\n" }
    }
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

private class FunctionNames(document: InflowDocument) {
    private val functionNames: Map<NodeId, String> = document.nodes.values
        .filter { !it.isLink }
        .sortedBy { it.id.value }
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
