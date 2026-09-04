package com.threadwork.app.ui

import java.awt.Color
import java.nio.file.Files
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import kotlin.test.Test
import kotlin.test.assertContains

class FopPdfWriterTest {
    @Test
    fun `writes vector page with extractable text`() {
        val output = Files.createTempFile("threadwork-searchable-", ".pdf")
        try {
            FopPdfWriter.write(
                output,
                listOf(
                    PdfVectorPage(210, 297) { graphics ->
                        graphics.color = Color.BLACK
                        graphics.drawRect(10, 10, 100, 60)
                        graphics.drawString("Searchable diagram label", 20, 40)
                    },
                ),
            )

            PDDocument.load(output.toFile()).use { document ->
                assertContains(PDFTextStripper().getText(document), "Searchable diagram label")
            }
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
