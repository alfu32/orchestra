package com.orchestra.app

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.naivekotlin.NaiveKotlinCompiler
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.PortDirection
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.validation.DocumentValidator
import com.orchestra.app.ui.launchDesktopApp
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.KotlinxJsonDocumentStore
import com.orchestra.storage.newDocument
import java.nio.file.Path
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    when (args.firstOrNull()) {
        null, "help", "--help", "-h" -> printHelp()
        "desktop" -> launchDesktopApp()
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
        Orchestra MVP CLI

        Commands:
          new <file.orch>                     Create a sample document
          validate <file.orch>                Validate document references
          compile <file.orch> <dir>           Export a naive Kotlin/JVM project
          desktop                             Open the graphical desktop editor
        """.trimIndent(),
    )
}

private fun createSample(args: Array<String>) {
    val file = args.getOrNull(1)?.let(Path::of) ?: error("Usage: new <file.orch>")
    val repository = InMemoryDocumentRepository(newDocument("Sample Orchestra Project"))
    val root = repository.getDocument().rootNodeId
    val producer = repository.createNode(root, "Producer", NodeKind.Processor)
    repository.addPort(producer.id, NodePort("out", "items", PortDirection.Output))
    repository.updateNodeTechnology(producer.id, kotlinTechnology())
    repository.updateNodeText(
        producer.id,
        producer.text.copy(source = "context.outputs.getOrPut(\"items\") { mutableListOf() }.add(\"hello\")"),
    )

    val consumer = repository.createNode(root, "Consumer", NodeKind.Processor)
    repository.addPort(consumer.id, NodePort("in", "items", PortDirection.Input))
    repository.updateNodeTechnology(consumer.id, kotlinTechnology())
    repository.updateNodeText(
        consumer.id,
        consumer.text.copy(source = "println(context.inputs[\"items\"] ?: emptyList<Any?>())"),
    )

    repository.createLink(root, "Producer.items -> Consumer.items", producer.id, "items", consumer.id, "items")
    KotlinxJsonDocumentStore().save(repository.getDocument(), file)
    println("Created ${file.toAbsolutePath()}")
}

private fun validate(args: Array<String>) {
    val file = args.getOrNull(1)?.let(Path::of) ?: error("Usage: validate <file.orch>")
    val document = KotlinxJsonDocumentStore().load(file)
    val diagnostics = DocumentValidator.validate(document)
    diagnostics.forEach { println("${it.severity}: ${it.message}") }
    if (diagnostics.none { it.severity == DiagnosticSeverity.Error }) {
        println("Document is valid")
    }
}

private fun compile(args: Array<String>) {
    val file = args.getOrNull(1)?.let(Path::of) ?: error("Usage: compile <file.orch> <dir>")
    val output = args.getOrNull(2)?.let(Path::of) ?: error("Usage: compile <file.inflow.json> <dir>")
    val document = KotlinxJsonDocumentStore().load(file)
    val result = NaiveKotlinCompiler().compile(document, CompilerOptions(projectName = document.name))
    result.diagnostics.forEach { println("${it.severity}: ${it.message}") }
    val generatedProject = result.generatedProject
    if (!result.success || generatedProject == null) error("Compilation failed")
    output.createDirectories()
    generatedProject.writeTo(output)
    println("Wrote ${generatedProject.files.size} files to ${output.toAbsolutePath()}")
}

private fun kotlinTechnology() = TechnologyMetadata(
    languageId = "kotlin",
    technologyId = "kotlin-jvm",
    compilerId = "naive-kotlin",
    fileExtension = "kt",
    contentType = "text/x-kotlin",
)
