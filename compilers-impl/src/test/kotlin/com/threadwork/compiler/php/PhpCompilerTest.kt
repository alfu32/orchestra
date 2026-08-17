package com.threadwork.compiler.php

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class PhpCompilerTest {
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
    fun `link instantiation uses link reference and qualified endpoint references`() {
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
        assertTrue(
            project.files.any {
                it.content.contains("transport(\$context, 'input_record', 'read_file.record_out', 'write_file.record_in');")
            },
        )
    }
}
