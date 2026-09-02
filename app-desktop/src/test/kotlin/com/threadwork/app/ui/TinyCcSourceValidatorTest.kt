package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.GeneratedSourceMap
import com.threadwork.compiler.api.GeneratedSourceMapEntry
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TinyCcSourceValidatorTest {
    @Test
    fun `TinyCC diagnostics map generated C lines to editable node source`() {
        val file = GeneratedFile(
            path = "generated.c",
            content = "int main(void) {\n    int broken = ;\n    return 0;\n}",
            originNodeId = null,
            reason = "test",
            sourceMap = GeneratedSourceMap(
                listOf(
                    GeneratedSourceMapEntry(
                        generatedLine = 2,
                        nodeId = NodeId("worker"),
                        textSection = NodeTextSection.Declaration,
                        sourceLine = 7,
                        generatedColumnOffset = 4,
                    ),
                ),
            ),
        )

        val diagnostics = TinyCcSourceValidator.validate(file)

        assertTrue(diagnostics.isNotEmpty())
        assertEquals(NodeId("worker"), diagnostics.first().nodeId)
        assertEquals(NodeTextSection.Declaration, diagnostics.first().textSection)
        assertEquals(7, diagnostics.first().line)
    }

    @Test
    fun `TinyCC validation recovers after its per-pass error limit`() {
        val file = GeneratedFile(
            path = "generated.c",
            content = """
                int main(void) {
                    int first = ;
                    int second = ;
                    int third = ;
                    return 0;
                }
            """.trimIndent(),
            originNodeId = null,
            reason = "test",
            sourceMap = GeneratedSourceMap(
                listOf(
                    GeneratedSourceMapEntry(2, NodeId("worker"), NodeTextSection.Declaration, 2),
                    GeneratedSourceMapEntry(3, NodeId("worker"), NodeTextSection.Declaration, 3),
                    GeneratedSourceMapEntry(4, NodeId("worker"), NodeTextSection.Declaration, 4),
                ),
            ),
        )

        val diagnostics = TinyCcSourceValidator.validate(file)

        assertEquals(setOf(2, 3, 4), diagnostics.mapNotNull { it.line }.toSet())
    }
}
