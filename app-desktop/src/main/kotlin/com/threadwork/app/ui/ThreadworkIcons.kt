package com.threadwork.app.ui

import java.awt.Image
import javax.swing.ImageIcon

object ThreadworkIcons {
    private const val BUTTON_ICON_ROOT = "icons/24/"
    private const val APP_ICON_ROOT = "icons/app/"

    private val cache = mutableMapOf<String, ImageIcon?>()

    fun buttonIcon(id: String): ImageIcon? = load("$BUTTON_ICON_ROOT$id.png")

    fun appIconImage(): Image? =
        listOf(256, 128, 64, 48, 32, 16)
            .asSequence()
            .mapNotNull { size -> load("$APP_ICON_ROOT$size.png")?.image }
            .firstOrNull()
            ?: buttonIcon("document")?.image

    private fun load(path: String): ImageIcon? =
        cache.getOrPut(path) {
            ThreadworkIcons::class.java.classLoader.getResource(path)?.let(::ImageIcon)
        }
}
