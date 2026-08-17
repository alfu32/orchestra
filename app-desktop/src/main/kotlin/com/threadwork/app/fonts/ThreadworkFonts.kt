package com.threadwork.app.fonts

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.prefs.Preferences

data class FontOption(
    val id: String,
    val label: String,
    val resourcePath: String?,
    val fallbackFamily: String = Font.MONOSPACED,
)

object ThreadworkFonts {
    private const val PREF_NODE = "com/threadwork/app/fonts"
    private const val DESIGNER_KEY = "designerFont"
    private const val CODE_KEY = "codeFont"
    private const val DEFAULT_DESIGNER = "d-din-regular"
    private const val DEFAULT_CODE = "monaspace-neon-regular"

    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE)
    private val loadedFonts = mutableMapOf<String, Font>()

    val designerOptions = listOf(
        systemMonospace(),
        fontOption("d-din-regular", "D-DIN Regular", "fonts/d-din/D-DIN.otf"),
        fontOption("d-din-bold", "D-DIN Bold", "fonts/d-din/D-DIN-Bold.otf"),
        fontOption("d-din-italic", "D-DIN Italic", "fonts/d-din/D-DIN-Italic.otf"),
        fontOption("alte-din-regular", "Alte DIN 1451 Mittelschrift", "fonts/alte-din-1451-mittelschrift/din1451alt.ttf"),
        fontOption("alte-din-grid", "Alte DIN 1451 Mittelschrift G", "fonts/alte-din-1451-mittelschrift/din1451alt G.ttf"),
        fontOption("monaspace-krypton-regular", "Monaspace Krypton Regular", "fonts/Monaspace Krypton/MonaspaceKrypton-Regular.otf"),
        fontOption("monaspace-krypton-light", "Monaspace Krypton Light", "fonts/Monaspace Krypton/MonaspaceKrypton-Light.otf"),
        fontOption("monaspace-krypton-bold", "Monaspace Krypton Bold", "fonts/Monaspace Krypton/MonaspaceKrypton-Bold.otf"),
        fontOption("monaspace-krypton-italic", "Monaspace Krypton Italic", "fonts/Monaspace Krypton/MonaspaceKrypton-Italic.otf"),
    )

    val codeOptions = listOf(
        systemMonospace(),
        fontOption("monaspace-neon-regular", "Monaspace Neon Regular", "fonts/Monaspace Neon/MonaspaceNeon-Regular.otf"),
        fontOption("monaspace-neon-light", "Monaspace Neon Light", "fonts/Monaspace Neon/MonaspaceNeon-Light.otf"),
        fontOption("monaspace-neon-bold", "Monaspace Neon Bold", "fonts/Monaspace Neon/MonaspaceNeon-Bold.otf"),
        fontOption("monaspace-neon-italic", "Monaspace Neon Italic", "fonts/Monaspace Neon/MonaspaceNeon-Italic.otf"),
    )

    var designerFontId: String
        get() = preferences.get(DESIGNER_KEY, DEFAULT_DESIGNER)
        set(value) = preferences.put(DESIGNER_KEY, validId(value, designerOptions, DEFAULT_DESIGNER))

    var codeFontId: String
        get() = preferences.get(CODE_KEY, DEFAULT_CODE)
        set(value) = preferences.put(CODE_KEY, validId(value, codeOptions, DEFAULT_CODE))

    fun designerFont(size: Float): Font = fontFor(designerFontId, designerOptions, size)

    fun codeFont(size: Float): Font = fontFor(codeFontId, codeOptions, size)

    fun optionLabel(id: String, options: List<FontOption>): String =
        options.firstOrNull { it.id == id }?.label ?: options.first().label

    fun optionId(label: String, options: List<FontOption>): String =
        options.firstOrNull { it.label == label }?.id ?: options.first().id

    private fun fontFor(id: String, options: List<FontOption>, size: Float): Font {
        val option = options.firstOrNull { it.id == id } ?: options.first()
        val font = option.resourcePath?.let { loadFont(option.id, it) }
        return (font ?: Font(option.fallbackFamily, Font.PLAIN, size.toInt())).deriveFont(size)
    }

    private fun loadFont(id: String, resourcePath: String): Font? =
        loadedFonts.getOrPut(id) {
            val stream = ThreadworkFonts::class.java.classLoader.getResourceAsStream(resourcePath)
                ?: return null
            stream.use {
                Font.createFont(Font.TRUETYPE_FONT, it).also { font ->
                    GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font)
                }
            }
        }

    private fun validId(value: String, options: List<FontOption>, fallback: String): String =
        value.takeIf { candidate -> options.any { it.id == candidate } } ?: fallback

    private fun fontOption(id: String, label: String, resourcePath: String): FontOption =
        FontOption(id, label, resourcePath)

    private fun systemMonospace(): FontOption =
        FontOption("system-monospace", "System Monospaced", null)
}
