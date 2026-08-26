package com.threadwork.compiler.php

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class PhpCompilerTest {
    @Test
    fun `declared types are generated for typed links`() {
        val repository = InMemoryDocumentRepository(newDocument("Typed PHP"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
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

        val result = PhpCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = requireNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("final class WorkOrder"))
        assertTrue(generated.contains("public float \$id"))
        assertTrue(generated.contains("transport_orders(\$orders_a_port, \$orders_b_port)"))
    }

    @Test
    fun `single file layout inlines child declarations without require statements`() {
        val repository = InMemoryDocumentRepository(newDocument("Single PHP"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "read_data", NodeKind.Processor)
        repository.createNode(root, "write_data", NodeKind.Processor)

        val result = PhpCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Single PHP"))

        assertTrue(result.success)
        val source = requireNotNull(result.generatedProject).files
            .filter { it.path.endsWith(".php") }
            .joinToString("\n") { it.content }
        assertTrue(source.contains("function read_data"))
        assertTrue(source.contains("function write_data"))
        assertTrue(source.contains("function Single_PHP"))
        assertTrue(!source.contains("require_once"))
        assertTrue(source.windowed("<?php".length).count { it == "<?php" } == 1)
    }

    @Test
    fun `link compilation generates named double buffered transport`() {
        val repository = InMemoryDocumentRepository(newDocument("Transport Project"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        val source = repository.createNode(root, "read_file", NodeKind.Processor)
        val target = repository.createNode(root, "write_file", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "record_out", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "record_in", PortDirection.Input))
        repository.createLink(root, "input_record", source.id, "record_out", target.id, "record_in")

        val result = PhpCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Transport Project"))

        assertTrue(result.success)
        val project = requireNotNull(result.generatedProject)
        val generated = project.files.joinToString("\n") { it.content }
        assertTrue(generated.contains("\$input_record_a_port = [];"))
        assertTrue(generated.contains("\$input_record_b_port = [];"))
        assertTrue(generated.contains("function transport_input_record(array &\$a, array &\$b)"))
        assertTrue(generated.contains("read_file(\$context, \$input_record_a_port);"))
        assertTrue(generated.contains("write_file(\$context, \$input_record_b_port);"))
        assertTrue(generated.contains("transport_input_record(\$input_record_a_port, \$input_record_b_port);"))
    }
}
