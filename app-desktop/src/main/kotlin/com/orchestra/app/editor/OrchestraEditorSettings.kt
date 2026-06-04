package com.orchestra.app.editor

import java.util.prefs.Preferences

object OrchestraEditorSettings {
    private const val PREF_NODE = "com/orchestra/app/editor"
    private const val INDENT_SPACES_KEY = "indentSpaces"
    private const val DEFAULT_INDENT_SPACES = 4

    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE)

    var indentSpaces: Int
        get() = preferences.getInt(INDENT_SPACES_KEY, DEFAULT_INDENT_SPACES).coerceAtLeast(1)
        set(value) = preferences.putInt(INDENT_SPACES_KEY, value.coerceAtLeast(1))
}
