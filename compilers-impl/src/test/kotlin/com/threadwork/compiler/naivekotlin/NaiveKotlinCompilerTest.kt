package com.threadwork.compiler.naivekotlin

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.compiler.generated.nodejs.JSCompiler
import com.threadwork.compiler.generic.CompilerCompiler
import com.threadwork.compiler.generic.GenericCompiler
import com.threadwork.compiler.php.PhpCompiler
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaiveKotlinCompilerTest {
    @Test
    fun `declared types are generated for typed links`() {
        val repository = InMemoryDocumentRepository(newDocument("Typed Kotlin"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "kotlin", technologyId = "kotlin-jvm"))
        val type = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "number"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))

        val result = NaiveKotlinCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = assertNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("data class WorkOrder"))
        assertTrue(generated.contains("val id: Double"))
        assertTrue(generated.contains("ArrayDeque<WorkOrder>"))
        assertTrue(generated.contains("transport_orders1(orders1_a_port, orders1_b_port)"))
    }

    @Test
    fun `single file kotlin layout emits runtime nodes and entry point together`() {
        val repository = InMemoryDocumentRepository(newDocument("Single Kotlin"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "kotlin", technologyId = "kotlin-jvm"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "producer", NodeKind.Processor)
        repository.createNode(root, "consumer", NodeKind.Processor)

        val result = NaiveKotlinCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Single Kotlin"))

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val sourceFiles = project.files.filter { it.path.endsWith(".kt") && !it.path.endsWith(".gradle.kts") }
        assertEquals(1, sourceFiles.size)
        val source = sourceFiles.single().content
        assertTrue(source.contains("class RuntimeContext"))
        assertTrue(source.contains("fun run_producer_"))
        assertTrue(source.contains("fun run_consumer_"))
        assertTrue(source.contains("fun main()"))
    }

    @Test
    fun `generates kotlin project files`() {
        val repository = InMemoryDocumentRepository(newDocument("Generated Sample"))
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "Producer", NodeKind.Processor)
        val target = repository.createNode(root, "Consumer", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "items", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "items", PortDirection.Input))
        repository.createLink(root, "transfer", source.id, "items", target.id, "items")

        val result = NaiveKotlinCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.path == "src/main/kotlin/generated/Runtime.kt" })
        val generated = project.files.joinToString("\n") { it.content }
        assertTrue(generated.contains("val transfer1_a_port = ArrayDeque"))
        assertTrue(generated.contains("val transfer1_b_port = ArrayDeque"))
        assertTrue(generated.contains("fun transport_transfer1("))
        assertTrue(generated.contains("run_producer_") && generated.contains("transfer1_a_port"))
        assertTrue(generated.contains("run_consumer_") && generated.contains("transfer1_b_port"))
        assertTrue(generated.contains("transport_transfer1(transfer1_a_port, transfer1_b_port)"))
    }

    @Test
    fun `structured compilers expose generated output as virtual files`() {
        val jsRepository = InMemoryDocumentRepository(newDocument("Storage Sample"))
        val jsRoot = jsRepository.getDocument().rootNodeId
        jsRepository.updateNodeTechnology(jsRoot, TechnologyMetadata(languageId = "javascript", technologyId = "nodejs"))
        jsRepository.updateNodeText(
            jsRoot,
            jsRepository.requireNode(jsRoot).text.copy(declaration = "context.value = 1;"),
        )

        val jsFiles = JSCompiler().store(
            jsRepository.getDocument(),
            jsRepository.requireNode(jsRoot),
            CompilerOptions(projectName = "Stored JS"),
        )

        assertTrue(jsFiles.any { it.path == "Stored_JS/package.json" })
        assertTrue(jsFiles.any { it.path == "Stored_JS/Storage_Sample.js" && it.content.contains("function Storage_Sample") })

        val phpRepository = InMemoryDocumentRepository(newDocument("PHP Sample"))
        val phpRoot = phpRepository.getDocument().rootNodeId
        phpRepository.updateNodeTechnology(phpRoot, TechnologyMetadata(languageId = "php", technologyId = "php"))
        phpRepository.updateNodeText(
            phpRoot,
            phpRepository.requireNode(phpRoot).text.copy(declaration = "\$context['value'] = 1;"),
        )

        val phpFiles = PhpCompiler().store(phpRepository.getDocument(), phpRepository.requireNode(phpRoot))

        assertTrue(phpFiles.any { it.path == "PHP_Sample/composer.json" })
        assertTrue(phpFiles.any { it.path == "PHP_Sample/PHP_Sample.php" && it.content.contains("function PHP_Sample") })
    }

    @Test
    fun `compiler compiler exposes generated compiler as virtual files`() {
        val repository = InMemoryDocumentRepository(newDocument("Compiler Design"))
        val root = repository.getDocument().rootNodeId
        val compiler = repository.createNode(root, "@Compiler", NodeKind.Processor)
        repository.updateNodeTechnology(compiler.id, TechnologyMetadata(languageId = "kotlin", technologyId = "generated-kotlin"))
        compiler.metadata["className"] = "FlowGeneratedCompiler"
        val generatorTemplate = repository.createNode(compiler.id, "@Generator", NodeKind.Processor)
        repository.updateNodeText(generatorTemplate.id, generatorTemplate.text.copy(declaration = "generated ${'$'}{node.name}"))

        val files = CompilerCompiler().store(repository.getDocument(), compiler)

        assertTrue(files.any { it.path == "src/main/kotlin/generated/compiler/FlowGeneratedCompiler.kt" })
        assertTrue(files.any { it.content.contains("class FlowGeneratedCompiler : GenericCompiler()") })
    }

    @Test
    fun `generic compiler emits static files encoded in the design`() {
        val repository = InMemoryDocumentRepository(newDocument("Generic Sample"))
        val root = repository.getDocument().rootNodeId
        val staticFile = repository.createNode(root, "@StaticFile", NodeKind.Processor)
        staticFile.metadata["path"] = "config/app.json"
        repository.updateNodeText(staticFile.id, staticFile.text.copy(declaration = """{"enabled":true}"""))

        val result = GenericCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val file = assertNotNull(project.files.singleOrNull { it.path == "config/app.json" })
        assertEquals("""{"enabled":true}""", file.content)
        assertEquals(GeneratedElementKind.StaticFile, file.elementKind)
    }

    @Test
    fun `generic compiler applies override templates to nodes and links`() {
        val repository = InMemoryDocumentRepository(newDocument("Generic Sample"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "generic"))
        val generatorTemplate = repository.createNode(root, "@Generator", NodeKind.Processor)
        repository.updateNodeText(generatorTemplate.id, generatorTemplate.text.copy(declaration = "generate ${'$'}{node.name} -> ${'$'}{outgoingArguments}\n${'$'}{outgoingTypeDefinitions}"))
        val sinkTemplate = repository.createNode(root, "@Sink", NodeKind.Processor)
        repository.updateNodeText(sinkTemplate.id, sinkTemplate.text.copy(declaration = "sink ${'$'}{node.name} <- ${'$'}{incomingArguments}\n${'$'}{incomingTypeDefinitions}"))
        val linkTemplate = repository.createNode(root, "@Transport", NodeKind.Processor)
        repository.updateNodeText(linkTemplate.id, linkTemplate.text.copy(declaration = "pipe ${'$'}{link.variableName}:${'$'}{link.typeName}\n${'$'}{link.typeDefinition}"))
        val source = repository.createNode(root, "read_file", NodeKind.Processor)
        val target = repository.createNode(root, "write_file", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "record", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "record", PortDirection.Input))
        val link = repository.createLink(root, "record", source.id, "record", target.id, "record")
        link.link?.typeName = "InputRecord"
        link.link?.payloadDefinition = "data class InputRecord(val value: String)"

        val result = GenericCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.none { it.path.endsWith(".template") })
        assertTrue(project.files.any { it.path == "nodes/read_file.md" && it.content == "generate read_file -> record:InputRecord\ndata class InputRecord(val value: String)" })
        assertTrue(project.files.any { it.path == "nodes/write_file.md" && it.content == "sink write_file <- record:InputRecord\ndata class InputRecord(val value: String)" })
        assertTrue(project.files.any { it.path == "links/record.md" && it.content == "pipe record:InputRecord\ndata class InputRecord(val value: String)" })
    }

    @Test
    fun `compiler compiler emits compiler class from a single compiler node`() {
        val repository = InMemoryDocumentRepository(newDocument("Compiler Design"))
        val root = repository.getDocument().rootNodeId
        val compiler = repository.createNode(root, "@Compiler", NodeKind.Processor)
        repository.updateNodeTechnology(compiler.id, TechnologyMetadata(languageId = "kotlin", technologyId = "generated-kotlin"))
        compiler.metadata["className"] = "FlowGeneratedCompiler"
        val generatorTemplate = repository.createNode(compiler.id, "@Generator", NodeKind.Processor)
        repository.updateNodeText(generatorTemplate.id, generatorTemplate.text.copy(declaration = "generated ${'$'}{node.name}"))
        val staticFilesTemplate = repository.createNode(compiler.id, "@StaticFile", NodeKind.Processor)
        repository.updateNodeText(staticFilesTemplate.id, staticFilesTemplate.text.copy(declaration = "settings.gradle.kts\nbuild.gradle.kts"))
        val projectFileTemplate = repository.createNode(compiler.id, "@ProjectFile", NodeKind.Processor)
        projectFileTemplate.metadata["path"] = "{{ projectName }}/settings.gradle.kts"
        repository.updateNodeText(projectFileTemplate.id, projectFileTemplate.text.copy(declaration = "rootProject.name = '{{ projectName }}'"))

        val result = CompilerCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val file = assertNotNull(project.files.singleOrNull { it.path == "src/main/kotlin/generated/compiler/FlowGeneratedCompiler.kt" })
        assertTrue(file.content.contains("class FlowGeneratedCompiler : GenericCompiler()"))
        assertTrue(file.content.contains("override fun templateOverrideFor(key: String): String?"))
        assertTrue(file.content.contains("generated ${'$'}{'$'}{node.name}"))
        assertTrue(file.content.contains("listOf(\"settings.gradle.kts\", \"build.gradle.kts\")"))
        assertTrue(file.content.contains("TemplateGeneratedFile"))
        assertTrue(file.content.contains("{{ projectName }}/settings.gradle.kts"))
    }

    @Test
    fun `compiler compiler does not inherit generated technology id from project root`() {
        val repository = InMemoryDocumentRepository(newDocument("Compiler Design"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "generic"))
        val compiler = repository.createNode(root, "@Compiler", NodeKind.Processor)
        repository.updateNodeTechnology(compiler.id, TechnologyMetadata(languageId = "kotlin"))

        val result = CompilerCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val file = assertNotNull(result.generatedProject?.files?.singleOrNull())
        assertTrue(file.content.contains("override val supportedTechnologyIds: Set<String> = setOf(\"compiler-design\")"))
    }
}
