package com.threadwork.app.ui

import java.awt.Rectangle
import kotlin.math.ceil
import kotlin.math.roundToInt

internal data class SheetTile(
    val row: Int,
    val column: Int,
    val sheet: Rectangle,
)

internal data class SheetTileLayout(
    val tiles: List<SheetTile>,
    val rows: Int,
    val columns: Int,
    val overlap: Int,
    val drawing: Rectangle,
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

        val overlap = if (multipage) {
            requestedOverlap.coerceIn(0, minOf(sheetWidth, sheetHeight) - 1)
        } else {
            0
        }
        val requiredWidth = contentBounds.width + margin * 2
        val requiredHeight = contentBounds.height + margin * 2
        val columnCount = if (multipage) pageCount(requiredWidth, sheetWidth, overlap) else 1
        val rowCount = if (multipage) pageCount(requiredHeight, sheetHeight, overlap) else 1
        val horizontalStep = sheetWidth - overlap
        val verticalStep = sheetHeight - overlap
        val coveredWidth = sheetWidth + (columnCount - 1) * horizontalStep
        val coveredHeight = sheetHeight + (rowCount - 1) * verticalStep
        val contentCenterX = contentBounds.x + contentBounds.width / 2.0
        val contentCenterY = contentBounds.y + contentBounds.height / 2.0
        val firstSheetX = (contentCenterX - coveredWidth / 2.0).roundToInt()
        val firstSheetY = (contentCenterY - coveredHeight / 2.0).roundToInt()

        val tiles = buildList {
            repeat(rowCount) { row ->
                repeat(columnCount) { column ->
                    add(
                        SheetTile(
                            row = row,
                            column = column,
                            sheet = Rectangle(
                                firstSheetX + column * horizontalStep,
                                firstSheetY + row * verticalStep,
                                sheetWidth,
                                sheetHeight,
                            ),
                        ),
                    )
                }
            }
        }
        val bounds = tiles.map(SheetTile::sheet).reduce(Rectangle::union)
        return SheetTileLayout(
            tiles = tiles,
            rows = rowCount,
            columns = columnCount,
            overlap = overlap,
            drawing = Rectangle(
                bounds.x + margin,
                bounds.y + margin,
                bounds.width - margin * 2,
                bounds.height - margin * 2,
            ),
        )
    }

    private fun pageCount(contentSize: Int, availableSize: Int, overlap: Int): Int {
        if (contentSize <= availableSize) return 1
        val step = availableSize - overlap
        return (1 + ceil((contentSize - availableSize).toDouble() / step).toInt()).coerceAtLeast(1)
    }
}
