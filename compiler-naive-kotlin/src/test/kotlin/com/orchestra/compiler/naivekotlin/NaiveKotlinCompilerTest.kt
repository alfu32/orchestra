package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.GeneratedElementKind
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.NodePort
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
}
