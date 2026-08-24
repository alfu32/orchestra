package com.threadwork.app.ui

import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class SheetTile(
    val row: Int,
    val column: Int,
    val sheet: Rectangle,
    val drawing: Rectangle,
)

internal data class SheetTileLayout(
    val tiles: List<SheetTile>,
    val rows: Int,
    val columns: Int,
    val overlap: Int,
) {
    val bounds: Rectangle = tiles
        .map(SheetTile::sheet)
        .reduce { accumulated, rectangle -> accumulated.union(rectangle) }
}

internal object SheetLayout {
    fun tile(
        contentBounds: Rectangle,
        sheetWidth: Int,
        sheetHeight: Int,
        margin: Int,
        requestedOverlap: Int,
        multipage: Boolean,
    ): SheetTileLayout {
        require(sheetWidth > margin * 2) { "Sheet width must exceed its margins." }
        require(sheetHeight > margin * 2) { "Sheet height must exceed its margins." }

        val drawingWidth = sheetWidth - margin * 2
        val drawingHeight = sheetHeight - margin * 2
        val overlap = if (multipage) {
            requestedOverlap.coerceIn(0, minOf(drawingWidth, drawingHeight) - 1)
        } else {
            0
        }
        val columnCount = if (multipage) pageCount(contentBounds.width, drawingWidth, overlap) else 1
        val rowCount = if (multipage) pageCount(contentBounds.height, drawingHeight, overlap) else 1
        val horizontalStep = drawingWidth - overlap
        val verticalStep = drawingHeight - overlap
        val coveredWidth = drawingWidth + (columnCount - 1) * horizontalStep
        val coveredHeight = drawingHeight + (rowCount - 1) * verticalStep
        val contentCenterX = contentBounds.x + contentBounds.width / 2.0
        val contentCenterY = contentBounds.y + contentBounds.height / 2.0
        val firstDrawingX = (contentCenterX - coveredWidth / 2.0).roundToInt()
        val firstDrawingY = (contentCenterY - coveredHeight / 2.0).roundToInt()

        val tiles = buildList {
            repeat(rowCount) { row ->
                repeat(columnCount) { column ->
                    val drawing = Rectangle(
                        firstDrawingX + column * horizontalStep,
                        firstDrawingY + row * verticalStep,
                        drawingWidth,
                        drawingHeight,
                    )
                    add(
                        SheetTile(
                            row = row,
                            column = column,
                            sheet = Rectangle(
                                drawing.x - margin,
                                drawing.y - margin,
                                sheetWidth,
                                sheetHeight,
                            ),
                            drawing = drawing,
                        ),
                    )
                }
            }
        }
        return SheetTileLayout(tiles, rowCount, columnCount, overlap)
    }

    private fun pageCount(contentSize: Int, availableSize: Int, overlap: Int): Int {
        if (contentSize <= availableSize) return 1
        val step = availableSize - overlap
        return (1 + ceil((contentSize - availableSize).toDouble() / step).toInt()).coerceAtLeast(1)
    }
}
