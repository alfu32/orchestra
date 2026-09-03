package com.threadwork.compiler.api

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GeneratedSourceMapExportTest {
    @Test
    fun `binary generated files remain binary virtual files`() {
        val bytes = byteArrayOf(0, 1, 2, -1)
        val generated = GeneratedFile(
            path = "lib/example.so",
            content = "",
            originNodeId = NodeId("lib"),
            reason = "binary test",
            binaryContent = bytes,
        )

        assertContentEquals(bytes, generated.toVirtualFile().binaryContent)
    }

    @Test
    fun `generated source maps are exported beside their source file`() {
        val project = GeneratedProject(
            name = "sample",
            files = listOf(
                GeneratedFile(
                    path = "sample.c",
                    content = "int main(void) { return 0; }\n",
                    originNodeId = null,
                    reason = "test",
                    sourceMap = GeneratedSourceMap(
                        listOf(
                            GeneratedSourceMapEntry(
                                generatedLine = 1,
                                nodeId = NodeId("worker"),
                                textSection = NodeTextSection.Declaration,
                                sourceLine = 3,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val files = project.toVirtualFiles()

        assertEquals(listOf("sample.c", "sample.c.map.json"), files.map(VirtualFile::path))
        assertTrue(files.last().content.contains("\"nodeId\": \"worker\""))
        assertTrue(files.last().content.contains("\"generatedFile\": \"sample.c\""))
    }
}
