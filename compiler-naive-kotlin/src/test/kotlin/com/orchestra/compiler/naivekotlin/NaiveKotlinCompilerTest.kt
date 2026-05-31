package com.orchestra.compiler.naivekotlin

import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
import com.orchestra.core.model.PortDirection
import com.orchestra.storage.InMemoryDocumentRepository
import com.orchestra.storage.newDocument
import kotlin.test.Test
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
}
