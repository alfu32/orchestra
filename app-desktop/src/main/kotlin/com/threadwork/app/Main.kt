package com.threadwork.app

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.generated.nodejs.JSCompiler
import com.threadwork.compiler.generic.CompilerCompiler
import com.threadwork.compiler.generic.GenericCompiler
import com.threadwork.compiler.naivekotlin.NaiveKotlinCompiler
import com.threadwork.compiler.php.PhpCompiler
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.projectName
import com.threadwork.core.model.rootNode
import com.threadwork.core.validation.DocumentValidator
import com.threadwork.app.ui.defaultPluginsFolder
import com.threadwork.app.ui.launchDesktopApp
import com.threadwork.app.ui.loadCompilerPlugins
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.KotlinxJsonDocumentStore
import com.threadwork.storage.newDocument
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "help", "--help", "-h" -> printHelp()
        "desktop" -> launchDesktopApp(parseDesktopPluginsFolder(args.drop(1)))
        "new" -> createSample(args)
        "validate" -> validate(args)
        "compile" -> compile(args)
        else -> {
            System.err.println("Unknown command: ${args.first()}")
            printHelp()
        }
    }
}

private fun printHelp() {
    println(
        """
        Threadwork CLI

        Commands:
          new <file.orch>                     Create a sample document
          validate <file.orch> [--plugins <dir>]
                                              Validate document references and compiler plugins
          compile <file.orch> <dir> [--plugins <dir>]
                                              Export with the first matching compiler plugin
          desktop [--plugins <dir>]           Open the graphical desktop editor
        """.trimIndent(),
    )
}

private fun parseDesktopPluginsFolder(args: List<String>): Path? {
    if (args.isEmpty()) return null
    val index = args.indexOfFirst { it == "--plugins" || it == "--plugins-dir" }
    if (index < 0) error("Usage: desktop [--plugins <dir>]")
    return args.getOrNull(index + 1)?.let(Path::of) ?: error("Usage: desktop [--plugins <dir>]")
}

private fun createSample(args: Array<String>) {
    val file = args.getOrNull(1)?.let(Path::of) ?: error("Usage: new <file.orch>")
    val repository = InMemoryDocumentRepository(newDocument("Sample Threadwork Project"))
    val root = repository.getDocument().rootNodeId
    val producer = repository.createNode(root, "Producer", NodeKind.Processor)
    repository.addPort(producer.id, NodePort("out", "items", PortDirection.Output))
    repository.updateNodeTechnology(producer.id, kotlinTechnology())
    repository.updateNodeText(
        producer.id,
        producer.text.copy(declaration = "context.outputs.getOrPut(\"items\") { mutableListOf() }.add(\"hello\")"),
    )

    val consumer = repository.createNode(root, "Consumer", NodeKind.Processor)
    repository.addPort(consumer.id, NodePort("in", "items", PortDirection.Input))
    repository.updateNodeTechnology(consumer.id, kotlinTechnology())
    repository.updateNodeText(
        consumer.id,
        consumer.text.copy(declaration = "println(context.inputs[\"items\"] ?: emptyList<Any?>())"),
    )

    repository.createLink(root, "Producer.items -> Consumer.items", producer.id, "items", consumer.id, "items")
    KotlinxJsonDocumentStore().save(repository.getDocument(), file)
    println("Created ${file.toAbsolutePath()}")
}

private fun validate(args: Array<String>) {
    val commandArgs = args.drop(1)
    val positionals = positionalArgs(commandArgs)
    val file = positionals.getOrNull(0)?.let(Path::of) ?: error("Usage: validate <file.orch> [--plugins <dir>]")
    val pluginsFolder = parsePluginsFolderOrDefault(commandArgs)
    val document = KotlinxJsonDocumentStore().load(file)
    val compilerDiagnostics = compilersFrom(pluginsFolder)
        .filter { compiler -> runCatching { compiler.supports(document) }.getOrDefault(false) }
        .flatMap { compiler -> compiler.validate(document) }
    val diagnostics = DocumentValidator.validate(document) + compilerDiagnostics
    diagnostics.forEach { println("${it.severity}: ${it.message}") }
    if (diagnostics.none { it.severity == DiagnosticSeverity.Error }) {
        println("Document is valid")
    }
}

private fun compile(args: Array<String>) {
    val commandArgs = args.drop(1)
    val positionals = positionalArgs(commandArgs)
    val file = positionals.getOrNull(0)?.let(Path::of) ?: error("Usage: compile <file.orch> <dir> [--plugins <dir>]")
    val output = positionals.getOrNull(1)?.let(Path::of) ?: error("Usage: compile <file.orch> <dir> [--plugins <dir>]")
    val pluginsFolder = parsePluginsFolderOrDefault(commandArgs)
    val document = KotlinxJsonDocumentStore().load(file)
    val compilers = compilersFrom(pluginsFolder)
    val compiler = selectCompiler(document, compilers)
        ?: error("No compiler plugin supports ${file.fileName}")
    val result = compiler.compile(document, CompilerOptions(projectName = document.projectName(), compilerPlugins = compilers))
    result.diagnostics.forEach { println("${it.severity}: ${it.message}") }
    val generatedProject = result.generatedProject
    if (!result.success || generatedProject == null) error("Compilation failed")
    output.createDirectories()
    generatedProject.writeTo(output)
    println("Wrote ${generatedProject.files.size} files to ${output.toAbsolutePath()}")
}

private fun compilersFrom(pluginsFolder: Path): List<CompilerPlugin> =
    loadCompilerPlugins(pluginsFolder) + CompilerCompiler() + GenericCompiler() + NaiveKotlinCompiler() + JSCompiler() + PhpCompiler()

private fun selectCompiler(document: com.threadwork.core.model.ThreadworkDocument, compilers: List<CompilerPlugin>): CompilerPlugin? {
    val root = document.rootNode()
    val requestedCompilerId = root.technology.compilerId.trim()
    val requestedTechnologyId = document.effectiveTechnologyId(root.id)
    val supporting = compilers.filter { compiler -> runCatching { compiler.supports(document) }.getOrDefault(false) }
    return supporting.firstOrNull { it.id == requestedCompilerId } ?:
        supporting.firstOrNull { requestedTechnologyId.isNotBlank() && requestedTechnologyId in it.supportedTechnologyIds } ?:
        supporting.firstOrNull { requestedTechnologyId.isNotBlank() && it.providedTechnologies.any { tech -> tech.technologyId == requestedTechnologyId } } ?:
        supporting.firstOrNull()
}

private fun parsePluginsFolderOrDefault(args: List<String>): Path {
    val index = args.indexOfFirst { it == "--plugins" || it == "--plugins-dir" }
    if (index < 0) return defaultPluginsFolder()
    return args.getOrNull(index + 1)?.let(Path::of) ?: error("Missing plugins directory after ${args[index]}")
}

private fun positionalArgs(args: List<String>): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--plugins",
            "--plugins-dir" -> index += 2
            else -> {
                result += args[index]
                index += 1
            }
        }
    }
    return result
}

private fun kotlinTechnology() = TechnologyMetadata(
    languageId = "kotlin",
    technologyId = "kotlin-jvm",
    compilerId = "naive-kotlin",
    fileExtension = "kt",
    contentType = "text/x-kotlin",
)
