package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.generic.CompilerCompiler
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.core.model.PortDirection
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NaiveKotlinCompilerTest {
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
        assertTrue(project.files.any { it.content.contains("runLink(context") })
    }

    @Test
    fun `generic compiler emits static files encoded in the design`() {
        val repository = InMemoryDocumentRepository(newDocument("Generic Sample"))
        val root = repository.getDocument().rootNodeId
        val staticFile = repository.createNode(root, "@StaticFile", NodeKind.Processor)
        staticFile.metadata["path"] = "config/app.json"
        repository.updateNodeText(staticFile.id, staticFile.text.copy(source = """{"enabled":true}"""))

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
        repository.updateNodeText(generatorTemplate.id, generatorTemplate.text.copy(source = "generate ${'$'}{node.name} -> ${'$'}{outgoingArguments}\n${'$'}{outgoingTypeDefinitions}"))
        val sinkTemplate = repository.createNode(root, "@Sink", NodeKind.Processor)
        repository.updateNodeText(sinkTemplate.id, sinkTemplate.text.copy(source = "sink ${'$'}{node.name} <- ${'$'}{incomingArguments}\n${'$'}{incomingTypeDefinitions}"))
        val linkTemplate = repository.createNode(root, "@Transport", NodeKind.Processor)
        repository.updateNodeText(linkTemplate.id, linkTemplate.text.copy(source = "pipe ${'$'}{link.variableName}:${'$'}{link.typeName}\n${'$'}{link.typeDefinition}"))
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
        repository.updateNodeText(generatorTemplate.id, generatorTemplate.text.copy(source = "generated ${'$'}{node.name}"))
        val staticFilesTemplate = repository.createNode(compiler.id, "@StaticFile", NodeKind.Processor)
        repository.updateNodeText(staticFilesTemplate.id, staticFilesTemplate.text.copy(source = "settings.gradle.kts\nbuild.gradle.kts"))

        val result = CompilerCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        val file = assertNotNull(project.files.singleOrNull { it.path == "src/main/kotlin/generated/compiler/FlowGeneratedCompiler.kt" })
        assertTrue(file.content.contains("class FlowGeneratedCompiler : GenericCompiler()"))
        assertTrue(file.content.contains("override fun getGenerator"))
        assertTrue(file.content.contains("override fun compile(document: InflowDocument, options: CompilerOptions)"))
        assertTrue(file.content.contains("generated ${'$'}{'$'}{node.name}"))
        assertTrue(file.content.contains("listOf(\"settings.gradle.kts\", \"build.gradle.kts\")"))
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
