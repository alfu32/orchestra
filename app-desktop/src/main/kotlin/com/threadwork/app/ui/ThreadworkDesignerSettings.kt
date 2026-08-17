package com.threadwork.app.ui

import java.util.prefs.Preferences

object ThreadworkDesignerSettings {
    private const val PREF_NODE = "com/threadwork/app/designer"
    private const val COMPOSITE_TITLE_TARGET_SCREEN_PX_KEY = "compositeTitleTargetScreenPx"
    private const val COMPOSITE_REFERENCE_VIEWPORT_WIDTH_KEY = "compositeReferenceViewportWidth"
    private const val COMPOSITE_REFERENCE_VIEWPORT_HEIGHT_KEY = "compositeReferenceViewportHeight"

    const val DEFAULT_COMPOSITE_TITLE_TARGET_SCREEN_PX = 18.0
    const val DEFAULT_COMPOSITE_REFERENCE_VIEWPORT_WIDTH = 1500.0
    const val DEFAULT_COMPOSITE_REFERENCE_VIEWPORT_HEIGHT = 1000.0

    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE)

    var compositeTitleTargetScreenPx: Double
        get() = preferences
            .getDouble(COMPOSITE_TITLE_TARGET_SCREEN_PX_KEY, DEFAULT_COMPOSITE_TITLE_TARGET_SCREEN_PX)
            .coerceIn(8.0, 48.0)
        set(value) = preferences.putDouble(COMPOSITE_TITLE_TARGET_SCREEN_PX_KEY, value.coerceIn(8.0, 48.0))

    var compositeReferenceViewportWidth: Double
        get() = preferences
            .getDouble(COMPOSITE_REFERENCE_VIEWPORT_WIDTH_KEY, DEFAULT_COMPOSITE_REFERENCE_VIEWPORT_WIDTH)
            .coerceIn(400.0, 10000.0)
        set(value) = preferences.putDouble(COMPOSITE_REFERENCE_VIEWPORT_WIDTH_KEY, value.coerceIn(400.0, 10000.0))

    var compositeReferenceViewportHeight: Double
        get() = preferences
            .getDouble(COMPOSITE_REFERENCE_VIEWPORT_HEIGHT_KEY, DEFAULT_COMPOSITE_REFERENCE_VIEWPORT_HEIGHT)
            .coerceIn(300.0, 10000.0)
        set(value) = preferences.putDouble(COMPOSITE_REFERENCE_VIEWPORT_HEIGHT_KEY, value.coerceIn(300.0, 10000.0))
}
