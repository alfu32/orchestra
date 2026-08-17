buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("org.apache.xmlgraphics:batik-transcoder:1.17")
    }
}

import org.apache.batik.transcoder.TranscoderInput
import org.apache.batik.transcoder.TranscoderOutput
import org.apache.batik.transcoder.image.PNGTranscoder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import java.nio.charset.StandardCharsets
import java.nio.file.Files

plugins {
    `java-library`
}

val iconsSourceDir = layout.projectDirectory.dir("icons")
val generatedIconsDir = layout.buildDirectory.dir("generated-resources/icons")

sourceSets {
    main {
        resources {
            srcDir(".")
            srcDir(generatedIconsDir)
            include("icons/**/*.png")
            include("fonts/Monaspace Neon/MonaspaceNeon-Regular.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Bold.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Light.otf")
            include("fonts/Monaspace Neon/MonaspaceNeon-Italic.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Regular.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Bold.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Light.otf")
            include("fonts/Monaspace Krypton/MonaspaceKrypton-Italic.otf")
            include("fonts/d-din/D-DIN.otf")
            include("fonts/d-din/D-DIN-Bold.otf")
            include("fonts/d-din/D-DIN-Italic.otf")
            include("fonts/d-din/SIL Open Font License.txt")
            include("fonts/alte-din-1451-mittelschrift/din1451alt.ttf")
            include("fonts/alte-din-1451-mittelschrift/din1451alt G.ttf")
        }
    }
}

fun renderSvgToPng(source: java.io.File, target: java.io.File, width: Int, height: Int) {
    target.parentFile.mkdirs()
    val batikSource = source.takeIf { svg ->
        svg.readText().contains("context-stroke") || svg.readText().contains("context-fill")
    }?.let { svg ->
        Files.createTempFile("threadwork-svg-", ".svg").toFile().apply {
            writeText(
                svg.readText()
                    .replace("context-stroke", "#000000")
                    .replace("context-fill", "#000000"),
                StandardCharsets.UTF_8,
            )
            deleteOnExit()
        }
    } ?: source
    try {
        batikSource.inputStream().use { input ->
            target.outputStream().use { output ->
                PNGTranscoder().apply {
                    addTranscodingHint(PNGTranscoder.KEY_WIDTH, width.toFloat())
                    addTranscodingHint(PNGTranscoder.KEY_HEIGHT, height.toFloat())
                }.transcode(
                    TranscoderInput(input),
                    TranscoderOutput(output),
                )
            }
        }
        return
    } catch (_: Throwable) {
        // Fall back to the external rasterizers below. Batik is preferred but not always
        // compatible with the SVG dialect used by the sprite sheet.
    }
    if (batikSource != source) {
        batikSource.delete()
    }
    val command = when {
        runCatching { ProcessBuilder("inkscape", "--version").start().waitFor() == 0 }.getOrDefault(false) ->
            listOf(
                "inkscape",
                source.absolutePath,
                "--export-type=png",
                "--export-filename=${target.absolutePath}",
                "--export-width=$width",
                "--export-height=$height",
            )
        runCatching { ProcessBuilder("convert", "--version").start().waitFor() == 0 }.getOrDefault(false) ->
            listOf(
                "convert",
                source.absolutePath,
                "-resize",
                "${width}x$height",
                target.absolutePath,
            )
        else -> error("No SVG rasterizer available for ${source.name}.")
    }
    val process = ProcessBuilder(command)
        .directory(source.parentFile)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText()
    if (process.waitFor() != 0) {
        error("Rasterizing ${source.name} failed:\n$output")
    }
}

fun copyImage(image: BufferedImage, x: Int, y: Int, width: Int, height: Int): BufferedImage {
    val cropped = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = cropped.createGraphics()
    try {
        graphics.drawImage(image, 0, 0, width, height, x, y, x + width, y + height, null)
    } finally {
        graphics.dispose()
    }
    return cropped
}

val generateIcons by tasks.registering {
    group = "build"
    description = "Rasterizes the UI sprite sheet into 24x24 and 32x32 PNG assets."
    inputs.file(iconsSourceDir.file("icons.svg"))
    inputs.file(iconsSourceDir.file("icons.mapping.csv"))
    outputs.dir(generatedIconsDir)
    doLast {
        val sourceSvg = iconsSourceDir.file("icons.svg").asFile
        val mappingCsv = iconsSourceDir.file("icons.mapping.csv").asFile
        listOf(24, 32).forEach { cellSize ->
            val sheetPng = layout.buildDirectory.file("tmp/icons-sheet-$cellSize.png").get().asFile
            renderSvgToPng(sourceSvg, sheetPng, cellSize * 10, cellSize * 10)
            val sheet = ImageIO.read(sheetPng) ?: error("Could not rasterize $sourceSvg")
            mappingCsv.useLines { lines ->
                lines.drop(1)
                    .filter { it.isNotBlank() }
                    .forEach { row ->
                        val parts = row.split(',', '|')
                        require(parts.size >= 3) { "Invalid icon mapping row: $row" }
                        val name = parts[0].trim()
                        val line = parts[1].trim().toInt()
                        val column = parts[2].trim().toInt()
                        val icon = copyImage(sheet, column * cellSize, line * cellSize, cellSize, cellSize)
                        val target = generatedIconsDir.get().asFile.resolve("icons/$cellSize/$name.png")
                        target.parentFile.mkdirs()
                        ImageIO.write(icon, "png", target)
                    }
            }
        }
    }
}

val generateAppIcon by tasks.registering {
    group = "build"
    description = "Rasterizes the application icon into common desktop sizes when provided."
    inputs.file(iconsSourceDir.file("appicon.svg"))
    outputs.dir(generatedIconsDir)
    doLast {
        val sourceSvg = iconsSourceDir.file("appicon.svg").asFile
        if (!sourceSvg.exists() || sourceSvg.length() == 0L) return@doLast
        listOf(256, 128, 64, 48, 32, 16).forEach { size ->
            val target = generatedIconsDir.get().asFile.resolve("icons/app/$size.png")
            renderSvgToPng(sourceSvg, target, size, size)
        }
    }
}

tasks.named("processResources") {
    dependsOn(generateIcons, generateAppIcon)
}
