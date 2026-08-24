package com.threadwork.app.ui

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO

internal data class PdfRasterPage(
    val image: BufferedImage,
    val widthPoints: Double,
    val heightPoints: Double,
)

internal object RasterPdfWriter {
    fun write(path: Path, pages: List<PdfRasterPage>) {
        require(pages.isNotEmpty()) { "A PDF must contain at least one page." }

        val kids = pages.indices.joinToString(" ") { "${pageObjectNumber(it)} 0 R" }
        val objects = mutableListOf<ByteArray>()
        objects += bytes("<< /Type /Catalog /Pages 2 0 R >>\n")
        objects += bytes("<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>\n")
        pages.forEachIndexed { index, page ->
            val contentNumber = contentObjectNumber(index)
            val imageNumber = imageObjectNumber(index)
            val imageName = "Im$index"
            objects += bytes(
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${number(page.widthPoints)} ${number(page.heightPoints)}] " +
                    "/Resources << /XObject << /$imageName $imageNumber 0 R >> >> /Contents $contentNumber 0 R >>\n",
            )
            objects += pdfStream(
                bytes("q ${number(page.widthPoints)} 0 0 ${number(page.heightPoints)} 0 0 cm /$imageName Do Q\n"),
            )
            objects += pdfImageStream(page.image)
        }

        val output = ByteArrayOutputStream()
        output.write(bytes("%PDF-1.4\n"))
        val offsets = mutableListOf(0)
        objects.forEachIndexed { index, objectBytes ->
            offsets += output.size()
            output.write(bytes("${index + 1} 0 obj\n"))
            output.write(objectBytes)
            output.write(bytes("endobj\n"))
        }
        val xref = output.size()
        output.write(bytes("xref\n0 ${objects.size + 1}\n"))
        output.write(bytes("0000000000 65535 f \n"))
        offsets.drop(1).forEach { offset -> output.write(bytes(String.format("%010d 00000 n \n", offset))) }
        output.write(bytes("trailer << /Size ${objects.size + 1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n"))
        Files.write(path, output.toByteArray())
    }

    private fun pageObjectNumber(index: Int): Int = 3 + index * 3

    private fun contentObjectNumber(index: Int): Int = pageObjectNumber(index) + 1

    private fun imageObjectNumber(index: Int): Int = pageObjectNumber(index) + 2

    private fun pdfStream(content: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(bytes("<< /Length ${content.size} >>\nstream\n"))
        write(content)
        write(bytes("endstream\n"))
    }.toByteArray()

    private fun pdfImageStream(image: BufferedImage): ByteArray {
        val rgb = BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().use { graphics ->
            graphics.color = Color.WHITE
            graphics.fillRect(0, 0, rgb.width, rgb.height)
            graphics.drawImage(image, 0, 0, null)
        }
        val jpeg = ByteArrayOutputStream()
        ImageIO.write(rgb, "jpg", jpeg)
        val encoded = jpeg.toByteArray()
        return ByteArrayOutputStream().apply {
            write(
                bytes(
                    "<< /Type /XObject /Subtype /Image /Width ${image.width} /Height ${image.height} " +
                        "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${encoded.size} >>\nstream\n",
                ),
            )
            write(encoded)
            write(bytes("\nendstream\n"))
        }.toByteArray()
    }

    private fun number(value: Double): String = "%.3f".format(java.util.Locale.ROOT, value).trimEnd('0').trimEnd('.')

    private fun bytes(value: String): ByteArray = value.toByteArray(ISO_8859_1)

    private inline fun <T : java.awt.Graphics> T.use(block: (T) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
