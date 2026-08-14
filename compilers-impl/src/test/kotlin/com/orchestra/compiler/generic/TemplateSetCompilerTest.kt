package com.orchestra.compiler.generic

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.SingleFileLayoutStrategy
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
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
                    CompilerTemplateRoles.ProcessorDeclaration to "function {{ node.name }}() {}",
                    CompilerTemplateRoles.ProcessorInstantiation to "{{ node.name }}();",
                    CompilerTemplateRoles.CompositeDeclaration to "function {{ node.name }}() {\n{{ childInstantiations }}\n}",
                    CompilerTemplateRoles.CompositeSingleFile to """
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
    }

    @Test
    fun `generic compiler accepts descriptive override node names`() {
        val repository = InMemoryDocumentRepository(newDocument("Graph Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "generic"))
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
    fun `generic compiler renders project file templates from the graph`() {
        val repository = InMemoryDocumentRepository(newDocument("Graph Templates"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "javascript", technologyId = "generic"))
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
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "markdown", technologyId = "generic"))
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
