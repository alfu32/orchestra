package com.threadwork.app.ui

import java.awt.Image
import javax.swing.ImageIcon

object ThreadworkIcons {
    private const val BUTTON_ICON_ROOT = "icons/24/"
    private const val APP_ICON_ROOT = "icons/app/"

    private val cache = mutableMapOf<String, ImageIcon?>()

    fun buttonIcon(id: String): ImageIcon? = load("$BUTTON_ICON_ROOT$id.png")

    fun appIconImages(): List<Image> =
        listOf(512, 256, 128, 64, 48, 32, 24, 16)
            .mapNotNull { size -> load("$APP_ICON_ROOT$size.png")?.image }
            .ifEmpty { listOfNotNull(buttonIcon("document")?.image) }

    fun trayIconImage(): Image? =
        load("${APP_ICON_ROOT}24.png")?.image
            ?: load("${APP_ICON_ROOT}16.png")?.image

    fun titleBarIcon(): ImageIcon? =
        load("${APP_ICON_ROOT}24.png")
            ?: load("${APP_ICON_ROOT}32.png")

    private fun load(path: String): ImageIcon? =
        cache.getOrPut(path) {
            ThreadworkIcons::class.java.classLoader.getResource(path)?.let(::ImageIcon)
        }
}
