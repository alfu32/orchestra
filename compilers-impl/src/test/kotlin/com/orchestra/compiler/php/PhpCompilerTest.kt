package com.orchestra.compiler.php

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.PortDirection
import com.orchestra.core.model.TechnologyMetadata
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class PhpCompilerTest {
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
