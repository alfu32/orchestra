package com.threadwork.compiler.generic

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TemplateSetCompilerTest {
    @Test
    fun `template compiler renders role fallbacks collections and composite assembly`() {
        val repository = InMemoryDocumentRepository(newDocument("Template Sample"))
        val root = repository.getDocument().rootNodeId
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "plain", fileExtension = "txt"))
        repository.createNode(root, "produce", NodeKind.Processor)
        repository.createNode(root, "consume", NodeKind.Processor)
        val compiler = StringTemplateCompiler(
            id = "test-templates",
            displayName = "Test templates",
            templateSet = CompilerTemplateSet(
                templates = mapOf(
                    CompilerTemplateRoles.ProcessorForwardDeclaration to "forward {{ node.name }}",
                    CompilerTemplateRoles.ProcessorDeclaration to "function {{ node.name }}() {}",
                    CompilerTemplateRoles.ProcessorInstantiation to "{{ node.name }}();",
                    CompilerTemplateRoles.CompositeForwardDeclaration to "forward {{ node.name }}",
                    CompilerTemplateRoles.CompositeDeclaration to "function {{ node.name }}() {\n{{ childInstantiations }}\n}",
                    CompilerTemplateRoles.CompositeSingleFile to """
                        {{ forwardDeclarations }}
                        {% for child in childArtifacts %}// child {{ child.node.name }}
                        {% endfor %}{{ inlineChildDeclarations }}

                        {{ ownDeclaration }}
                    """.trimIndent(),
                ),
                fileExtension = "txt",
                defaultLayoutStrategy = SingleFileLayoutStrategy,
            ),
        )

        val result = compiler.compile(repository.getDocument(), CompilerOptions(projectName = "Template Sample"))

        assertTrue(result.success)
        val file = assertNotNull(result.generatedProject).files.single()
        assertTrue(file.content.contains("// child produce"))
        assertTrue(file.content.contains("// child consume"))
        assertTrue(file.content.contains("function produce() {}"))
        assertTrue(file.content.contains("function consume() {}"))
        assertTrue(file.content.contains("produce();"))
        assertTrue(file.content.contains("consume();"))
        assertTrue(file.content.indexOf("forward produce") < file.content.indexOf("function produce() {}"))
        assertTrue(file.content.indexOf("forward consume") < file.content.indexOf("function consume() {}"))
        assertTrue(file.content.lines().count { it.trim() == "forward produce" } == 1)
        assertTrue(file.content.lines().count { it.trim() == "forward consume" } == 1)
    }

    @Test
    fun `generic compiler accepts descriptive override node names`() {
        val repository = InMemoryDocumentRepository(newDocument("Graph Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "compiler-template-set"))
        val declarationTemplate = repository.createNode(root, "@ProcessorDeclaration", NodeKind.Processor)
        repository.updateNodeText(
            declarationTemplate.id,
            declarationTemplate.text.copy(declaration = "{% if node.isTerminal %}terminal {{ node.name }}{% endif %}"),
        )
        repository.createNode(root, "worker", NodeKind.Processor)

        val result = GenericCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.path == "nodes/worker.md" && it.content == "terminal worker" })
    }

    @Test
    fun `compiler template overrides can come from the source editor`() {
        val repository = InMemoryDocumentRepository(newDocument("Source Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "plain", technologyId = "compiler-template-set"))
        val declarationTemplate = repository.createNode(root, "@ProcessorDeclaration", NodeKind.Processor)
        repository.updateNodeText(
            declarationTemplate.id,
            declarationTemplate.text.copy(declaration = "source-template {{ node.name }}"),
        )
        repository.createNode(root, "worker", NodeKind.Processor)

        val result = GenericCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.content == "source-template worker" })
    }

    @Test
    fun `generic compiler assembles composites from graphical layout roles`() {
        val repository = InMemoryDocumentRepository(newDocument("Graph Assembly"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "plain", technologyId = "compiler-template-set", fileExtension = "txt"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        fun override(name: String, template: String) {
            val node = repository.createNode(root, name, NodeKind.Processor)
            repository.updateNodeText(node.id, node.text.copy(declaration = template))
        }
        override("@ProcessorDeclaration", "processor {{ node.name }}")
        override("@ProcessorInstantiation", "invoke {{ node.name }}")
        override("@CompositeDeclaration", "composite {{ node.name }} [{{ childInstantiations }}]")
        override("@CompositeSingleFile", "{{ inlineChildDeclarations }}\n{{ ownDeclaration }}")
        repository.createNode(root, "worker", NodeKind.Processor)

        val result = GenericCompiler().compile(repository.getDocument())

        assertTrue(result.success)
        val source = assertNotNull(result.generatedProject).files.single { it.originNodeId == root }.content
        assertTrue(source.contains("processor worker"))
        assertTrue(source.contains("composite Graph Assembly [invoke worker]"))
        assertTrue(!source.contains("@ProcessorDeclaration"))
    }

    @Test
    fun `generic compiler renders project file templates from the graph`() {
        val repository = InMemoryDocumentRepository(newDocument("Graph Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "compiler-template-set"))
        val projectFile = repository.createNode(root, "@ProjectFile", NodeKind.Processor)
        projectFile.metadata["path"] = "{{ projectName }}/package.json"
        repository.updateNodeText(
            projectFile.id,
            projectFile.text.copy(declaration = """{"name":"{{ projectName | lower }}"}"""),
        )

        val result = GenericCompiler().compile(
            repository.getDocument(),
            CompilerOptions(projectName = "TemplateApp"),
        )

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.path == "TemplateApp/package.json" && it.content == """{"name":"templateapp"}""" })
    }

    @Test
    fun `generated style compiler applies embedded roles without graph overrides`() {
        val repository = InMemoryDocumentRepository(newDocument("Embedded Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "compiler-template-set"))
        repository.createNode(root, "worker", NodeKind.Processor)
        val compiler = object : GenericCompiler() {
            override fun templateOverrideFor(key: String): String? =
                if (key == CompilerTemplateRoles.ProcessorDeclaration) "embedded {{ node.name }}" else null
        }

        val result = compiler.compile(repository.getDocument())

        assertTrue(result.success)
        val project = assertNotNull(result.generatedProject)
        assertTrue(project.files.any { it.path == "nodes/worker.md" && it.content == "embedded worker" })
    }
}
