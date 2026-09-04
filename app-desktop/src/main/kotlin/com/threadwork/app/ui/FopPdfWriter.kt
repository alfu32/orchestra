package com.threadwork.app.ui

import java.awt.Graphics2D
import java.nio.file.Files
import java.nio.file.Path
import org.apache.fop.svg.PDFDocumentGraphics2D
import org.apache.xmlgraphics.java2d.GraphicContext

internal data class PdfVectorPage(
    val widthPoints: Int,
    val heightPoints: Int,
    val draw: (Graphics2D) -> Unit,
)

/** Writes pages by replaying the diagram drawing commands into PDF graphics. */
internal object FopPdfWriter {
    fun write(path: Path, pages: List<PdfVectorPage>) {
        require(pages.isNotEmpty()) { "A PDF must contain at least one page." }

        Files.newOutputStream(path).use { output ->
            val first = pages.first()
            val graphics = PDFDocumentGraphics2D(false)
            graphics.setGraphicContext(GraphicContext())
            graphics.setupDefaultFontInfo()
            graphics.setupDocument(output, first.widthPoints, first.heightPoints)
            try {
                pages.forEachIndexed { index, page ->
                    if (index > 0) graphics.nextPage(page.widthPoints, page.heightPoints)
                    page.draw(graphics)
                }
            } finally {
                graphics.finish()
            }
        }
    }
}
