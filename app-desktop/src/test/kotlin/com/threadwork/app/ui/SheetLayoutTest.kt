package com.threadwork.app.ui

import java.awt.Font
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetLayoutTest {
    @Test
    fun `table columns follow measured content while fixed columns retain their width`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.font = Font(Font.MONOSPACED, Font.PLAIN, 10)
            val widths = SheetLayout.measuredColumnWidths(
                headers = listOf("No.", "Part", "Signature"),
                rows = listOf(
                    listOf("1", "short", ""),
                    listOf("2", "a much longer part", ""),
                ),
                metrics = graphics.fontMetrics,
                horizontalPadding = 8,
                fixedWidths = mapOf(2 to 110),
            )

            assertEquals(graphics.fontMetrics.stringWidth("No.") + 8, widths[0])
            assertEquals(graphics.fontMetrics.stringWidth("a much longer part") + 8, widths[1])
            assertEquals(110, widths[2])
        } finally {
            graphics.dispose()
        }
    }

    @Test
    fun `parts list reserves exactly one body row per component`() {
        assertEquals(40, SheetLayout.partsListHeight(componentCount = 0, rowHeight = 20))
        assertEquals(100, SheetLayout.partsListHeight(componentCount = 3, rowHeight = 20))
        assertEquals(320, SheetLayout.partsListHeight(componentCount = 14, rowHeight = 20))
    }

    @Test
    fun `disabled tiling centers one page on the content`() {
        val layout = SheetLayout.tile(
            contentBounds = Rectangle(100, 200, 900, 500),
            sheetWidth = 400,
            sheetHeight = 300,
            margin = 20,
            requestedOverlap = 20,
            multipage = false,
        )

        assertEquals(1, layout.rows)
        assertEquals(1, layout.columns)
        assertEquals(0, layout.overlap)
        assertEquals(550.0, layout.drawing.centerX)
        assertEquals(450.0, layout.drawing.centerY)
    }

    @Test
    fun `tiling covers a large bounding box with the requested overlap`() {
        val content = Rectangle(0, 0, 1_000, 600)
        val layout = SheetLayout.tile(
            contentBounds = content,
            sheetWidth = 400,
            sheetHeight = 300,
            margin = 20,
            requestedOverlap = 20,
            multipage = true,
        )

        assertEquals(3, layout.columns)
        assertEquals(3, layout.rows)
        assertEquals(20, layout.overlap)
        val first = layout.tiles.first().sheet
        val right = layout.tiles.first { it.row == 0 && it.column == 1 }.sheet
        val below = layout.tiles.first { it.row == 1 && it.column == 0 }.sheet
        assertEquals(20, first.x + first.width - right.x)
        assertEquals(20, first.y + first.height - below.y)
        assertTrue(layout.drawing.contains(content))
        assertEquals(
            Rectangle(
                layout.bounds.x + 20,
                layout.bounds.y + 20,
                layout.bounds.width - 40,
                layout.bounds.height - 40,
            ),
            layout.drawing,
        )
        assertTrue(layout.tiles.all { it.sheet.width == 400 && it.sheet.height == 300 })
    }
}
