package com.threadwork.app.ui

import com.formdev.flatlaf.FlatDarkLaf
import com.formdev.flatlaf.FlatLightLaf
import com.threadwork.core.classification.LinkStereotype
import java.awt.Color
import java.util.prefs.Preferences
import javax.swing.UIManager

enum class ApplicationTheme(val label: String) {
    Light("Light"),
    Dark("Dark"),
    ;

    companion object {
        fun fromLabel(label: String): ApplicationTheme =
            entries.firstOrNull { it.label == label } ?: Light
    }
}

enum class DesignerColorKey(
    val label: String,
    val light: Color,
    val dark: Color,
) {
    CanvasBackground("Canvas background", Color(0xf7f7f7), Color(0x1e1f22)),
    GridMinor("Grid minor", Color(0xe2e2e2), Color(0x2b2d30)),
    GridMajor("Grid major", Color(0xd1d1df), Color(0x3a3d43)),
    NodeFill("Default node fill", Color.WHITE, Color(0x2b2d30)),
    NodeStroke("Default node stroke", Color(0x222222), Color(0xf0f0f0)),
    TypeFill("Type node fill", Color(0xf1fbf9), Color(0x173f3b)),
    TypeStroke("Type node stroke", Color(0x00897b), Color(0x34c6b4)),
    TypeText("Type reference text", Color(0x008c4a), Color(0x58d68d)),
    CompilerFill("Compiler node fill", Color(0xfff4dc), Color(0x4c3b17)),
    CompilerStroke("Compiler node stroke", Color(0xaa6a00), Color(0xf0ad36)),
    LibraryFill("Service library fill", Color(0xf6f7ff), Color(0x292e43)),
    LibraryStroke("Service library stroke", Color(0x3333cc), Color(0x7887ff)),
    ErrorFill("Error node fill", Color(0xfffbfb), Color(0x432a2a)),
    ErrorStroke("Error node stroke", Color(0xcc3333), Color(0xff7777)),
    TestFill("Test node fill", Color(0xf8fff8), Color(0x273d2c)),
    TestStroke("Test node stroke", Color(0x33aa33), Color(0x6fd37a)),
    TextPrimary("Primary text", Color(0x222222), Color(0xf2f2f2)),
    TextSecondary("Secondary text", Color(0x555555), Color(0xcacaca)),
    TextMuted("Muted text", Color(0x666666), Color(0x9fa3aa)),
    LinkDefault("Data link", Color(0x222222), Color(0xf0f0f0)),
    LinkLibrary("Library link", Color(0x3333cc), Color(0x7887ff)),
    LinkError("Error link", Color(0xcc3333), Color(0xff7777)),
    LinkDependency("Dependency link", Color(0xb36b00), Color(0xf0ad36)),
    LinkSourceCapability("Source capability", Color(0x00796b), Color(0x4dd0c0)),
    LinkRunnableCapability("Runnable capability", Color(0x7b1fa2), Color(0xce93d8)),
    AnnotationFill("Annotation fill", Color(0xf8f9ff), Color(0x2b2d30)),
    PortFill("Port fill", Color.WHITE, Color(0x1e1f22)),
    Selection("Selection", Color(0x3366cc), Color(0x76a9ff)),
    ;
}

class DesignerPalette internal constructor(
    private val colors: Map<DesignerColorKey, Color>,
) {
    operator fun get(key: DesignerColorKey): Color = colors.getValue(key)

    fun withColor(key: DesignerColorKey, color: Color): DesignerPalette =
        DesignerPalette(colors + (key to color))
}

fun DesignerPalette.colorForLink(stereotype: LinkStereotype): Color = when (stereotype) {
    LinkStereotype.UsageImport -> this[DesignerColorKey.LinkLibrary]
    LinkStereotype.ErrorPipe -> this[DesignerColorKey.LinkError]
    LinkStereotype.DependencyInjection -> this[DesignerColorKey.LinkDependency]
    LinkStereotype.SourceCapability -> this[DesignerColorKey.LinkSourceCapability]
    LinkStereotype.RunnableCapability -> this[DesignerColorKey.LinkRunnableCapability]
    else -> this[DesignerColorKey.LinkDefault]
}

object ThreadworkAppearance {
    private const val PREF_NODE = "com/threadwork/app/appearance"
    private const val THEME_KEY = "theme"
    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE)

    var theme: ApplicationTheme
        get() = runCatching { ApplicationTheme.valueOf(preferences.get(THEME_KEY, ApplicationTheme.Light.name)) }
            .getOrDefault(ApplicationTheme.Light)
        set(value) = preferences.put(THEME_KEY, value.name)

    fun palette(theme: ApplicationTheme = this.theme): DesignerPalette =
        DesignerPalette(
            DesignerColorKey.entries.associateWith { key ->
                preferences.get(colorKey(theme, key), null)?.let(::colorFromHex) ?: defaultColor(theme, key)
            },
        )

    fun updatePalette(theme: ApplicationTheme, palette: DesignerPalette) {
        DesignerColorKey.entries.forEach { key ->
            preferences.put(colorKey(theme, key), colorToHex(palette[key]))
        }
    }

    fun applyLookAndFeel() {
        when (theme) {
            ApplicationTheme.Light -> FlatLightLaf.setup()
            ApplicationTheme.Dark -> FlatDarkLaf.setup()
        }
        UIManager.put("Component.arc", 6)
        UIManager.put("Button.arc", 6)
        UIManager.put("TextComponent.arc", 4)
        UIManager.put("TitlePane.centerTitle", false)
        UIManager.put("TitlePane.centerTitleIfMenuBarEmbedded", false)
        UIManager.put("TitlePane.showIcon", false)
        UIManager.put("TitlePane.showIconBesideTitle", false)
    }

    fun defaultPalette(theme: ApplicationTheme): DesignerPalette =
        DesignerPalette(DesignerColorKey.entries.associateWith { defaultColor(theme, it) })

    fun colorToHex(color: Color): String = "#%02X%02X%02X".format(color.red, color.green, color.blue)

    fun colorFromHex(value: String): Color? = runCatching {
        val hex = value.trim().removePrefix("#")
        require(hex.length == 6)
        Color(hex.toInt(16))
    }.getOrNull()

    private fun defaultColor(theme: ApplicationTheme, key: DesignerColorKey): Color =
        if (theme == ApplicationTheme.Dark) key.dark else key.light

    private fun colorKey(theme: ApplicationTheme, key: DesignerColorKey): String =
        "${theme.name.lowercase()}.${key.name.lowercase()}"
}
