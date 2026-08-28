package com.threadwork.app.ui

import java.awt.print.PrinterJob
import java.util.prefs.Preferences
import javax.print.PrintService
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject

internal data class SheetPaginationSettings(
    val formatChoice: String,
    val scaleChoice: String,
    val multipage: Boolean,
    val overlapMm: Double,
)

internal data class PrinterTarget(
    val key: String,
    val label: String,
    val service: PrintService?,
    val reachable: Boolean,
) {
    val isPdf: Boolean get() = key == PDF_PRINTER_KEY

    companion object {
        const val PDF_PRINTER_KEY = "threadwork:pdf"
    }
}

/**
 * Persists pagination preferences by stable printer key. Physical printers are rediscovered on
 * every dialog invocation; disconnected printers remain visible when they still have a profile.
 */
internal class ThreadworkPrintProfileStore(
    private val preferences: Preferences = Preferences.userRoot().node(PREF_NODE),
) {
    fun targets(): List<PrinterTarget> {
        val discovered = PrinterJob.lookupPrintServices()
            .sortedBy(PrintService::getName)
            .map { service ->
                PrinterTarget(
                    key = systemPrinterKey(service.name),
                    label = service.name,
                    service = service,
                    reachable = true,
                )
            }
        val configured = configuredProfiles().keys
        val reachableKeys = discovered.mapTo(mutableSetOf(PrinterTarget.PDF_PRINTER_KEY)) { it.key }
        val missing = configured
            .filterNot(reachableKeys::contains)
            .sorted()
            .map { key ->
                PrinterTarget(
                    key = key,
                    label = key.removePrefix(SYSTEM_PRINTER_KEY_PREFIX),
                    service = null,
                    reachable = false,
                )
            }
        return listOf(
            PrinterTarget(
                key = PrinterTarget.PDF_PRINTER_KEY,
                label = "Save as PDF",
                service = null,
                reachable = true,
            ),
        ) + discovered + missing
    }

    fun settingsFor(printerKey: String, fallback: SheetPaginationSettings): SheetPaginationSettings =
        configuredProfiles()[printerKey] ?: fallback

    fun save(printerKey: String, settings: SheetPaginationSettings) {
        val profiles = configuredProfiles().toMutableMap()
        profiles[printerKey] = settings.normalized()
        persist(profiles)
    }

    fun reset(printerKey: String) {
        val profiles = configuredProfiles().toMutableMap()
        profiles.remove(printerKey)
        persist(profiles)
    }

    fun remove(printerKey: String) = reset(printerKey)

    private fun configuredProfiles(): LinkedHashMap<String, SheetPaginationSettings> {
        val raw = preferences.get(PROFILES_KEY, "[]")
        val rows = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull().orEmpty()
        return rows.mapNotNull { row ->
            val value = row as? JsonObject ?: return@mapNotNull null
            val key = value.string("printer-key").trim()
            if (key.isBlank()) return@mapNotNull null
            key to SheetPaginationSettings(
                formatChoice = value.string("format", "Auto"),
                scaleChoice = value.string("scale", "1:1"),
                multipage = value.boolean("multipage", false),
                overlapMm = value.double("overlap-mm", 5.0),
            ).normalized()
        }.toMap(LinkedHashMap())
    }

    private fun persist(profiles: Map<String, SheetPaginationSettings>) {
        val content = buildJsonArray {
            profiles.toSortedMap().forEach { (key, settings) ->
                add(
                    buildJsonObject {
                        put("printer-key", JsonPrimitive(key))
                        put("format", JsonPrimitive(settings.formatChoice))
                        put("scale", JsonPrimitive(settings.scaleChoice))
                        put("multipage", JsonPrimitive(settings.multipage))
                        put("overlap-mm", JsonPrimitive(settings.overlapMm))
                    },
                )
            }
        }
        preferences.put(PROFILES_KEY, content.toString())
    }

    private fun SheetPaginationSettings.normalized(): SheetPaginationSettings =
        copy(
            formatChoice = formatChoice.ifBlank { "Auto" },
            scaleChoice = scaleChoice.ifBlank { "1:1" },
            overlapMm = overlapMm.coerceIn(0.0, 50.0),
        )

    private fun JsonObject.string(name: String, fallback: String = ""): String =
        (this[name] as? JsonPrimitive)?.content ?: fallback

    private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean =
        (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback

    private fun JsonObject.double(name: String, fallback: Double): Double =
        (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: fallback

    private companion object {
        const val PREF_NODE = "com/threadwork/app/print"
        const val PROFILES_KEY = "printer-profiles"
        const val SYSTEM_PRINTER_KEY_PREFIX = "system:"

        fun systemPrinterKey(name: String): String = "$SYSTEM_PRINTER_KEY_PREFIX$name"
    }
}
