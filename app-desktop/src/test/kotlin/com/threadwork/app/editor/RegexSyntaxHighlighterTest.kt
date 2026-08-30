package com.threadwork.app.editor

import com.threadwork.completion.DeclarationSymbol
import com.threadwork.completion.DeclarationSymbolKind
import com.threadwork.core.model.NodeId
import java.awt.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RegexSyntaxHighlighterTest {
    private val function = DeclarationSymbol(
        name = "transform_packet",
        kind = DeclarationSymbolKind.Function,
        header = "void transform_packet(void)",
        languageId = "c",
        ownerNodeId = NodeId("worker"),
        ownerNodeName = "worker",
        startOffset = 0,
        endOffset = 27,
    )

    @Test
    fun `highlights known declarations as semantic symbols`() {
        val line = "transform_packet();"
        val token = RegexSyntaxHighlighter.highlightLine("c", line, listOf(function)).single()

        assertEquals(0, token.start)
        assertEquals("transform_packet".length, token.endExclusive)
        assertNotEquals(RegexSyntaxHighlighter.Default, token.color)
    }

    @Test
    fun `does not semantically recolor identifiers inside strings`() {
        val line = "\"transform_packet\""
        val tokens = RegexSyntaxHighlighter.highlightLine("c", line, listOf(function))

        assertEquals(1, tokens.size)
        assertEquals(0, tokens.single().start)
        assertEquals(line.length, tokens.single().endExclusive)
    }

    @Test
    fun `uses linked identifier color without overriding string literals`() {
        val linkedColor = Color(0x00aa88)
        val line = "incomingPacket.push(\"incomingPacket\");"
        val tokens = RegexSyntaxHighlighter.highlightLine(
            "javascript",
            line,
            semanticIdentifierColors = mapOf("incomingPacket" to linkedColor),
        )

        assertEquals(linkedColor, tokens.first { it.start == 0 }.color)
        assertEquals("incomingPacket".length + 2, tokens.last().endExclusive - tokens.last().start)
        assertNotEquals(linkedColor, tokens.last().color)
    }
}
