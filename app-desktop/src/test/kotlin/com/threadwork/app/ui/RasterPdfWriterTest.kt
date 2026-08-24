package com.threadwork.app.ui

import java.awt.image.BufferedImage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains

class RasterPdfWriterTest {
    @Test
    fun `writes one PDF page object per raster page`() {
        val output = Files.createTempFile("threadwork-sheet-", ".pdf")
        try {
            val image = BufferedImage(32, 48, BufferedImage.TYPE_INT_RGB)
            RasterPdfWriter.write(
                output,
                listOf(
                    PdfRasterPage(image, 210.0, 297.0),
                    PdfRasterPage(image, 210.0, 297.0),
                ),
            )

            val pdf = Files.readString(output, Charsets.ISO_8859_1)
            assertContains(pdf, "/Count 2")
            assertContains(pdf, "/Kids [3 0 R 6 0 R]")
            assertContains(pdf, "/MediaBox [0 0 210 297]")
        } finally {
            Files.deleteIfExists(output)
        }
    }
}
