package com.threadwork.app.ui

import java.util.prefs.Preferences
import javax.swing.JSplitPane

internal object ThreadworkUiSettings {
    private const val PREF_NODE = "com/threadwork/app/ui"
    private val preferences = Preferences.userRoot().node(PREF_NODE)

    fun rememberDividerLocation(splitPane: JSplitPane, key: String, defaultLocation: Int? = null) {
        val stored = preferences.getInt(key, -1)
        when {
            stored >= 0 -> splitPane.dividerLocation = stored
            defaultLocation != null -> splitPane.dividerLocation = defaultLocation
        }
        splitPane.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY) {
            splitPane.dividerLocation.takeIf { it >= 0 }?.let { preferences.putInt(key, it) }
        }
    }
}
