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
    val pdfRenderMode: PdfRenderMode = PdfRenderMode.Rasterized,
)

internal enum class PdfRenderMode {
    Rasterized,
    Searchable,
}

/** Page geometry for reflowed documentation; unlike plans, documents do not use drawing scale. */
internal data class DocumentationPrintSettings(
    val formatChoice: String,
    val includeHeader: Boolean,
    val includeFooter: Boolean,
    val marginTopMm: Double,
    val marginLeftMm: Double,
    val marginRightMm: Double,
    val marginBottomMm: Double,
)

/** Separate pagination settings are retained for the plan and generated documentation. */
internal data class PrinterPaginationProfile(
    val plan: SheetPaginationSettings,
    val documentation: DocumentationPrintSettings,
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
                label = "Export",
                service = null,
                reachable = true,
            ),
        ) + discovered + missing
    }

    fun profileFor(
        printerKey: String,
        planFallback: SheetPaginationSettings,
        documentationFallback: DocumentationPrintSettings,
    ): PrinterPaginationProfile = configuredProfiles()[printerKey]
        ?: PrinterPaginationProfile(planFallback.normalized(), documentationFallback.normalized())

    fun save(printerKey: String, profile: PrinterPaginationProfile) {
        val profiles = configuredProfiles().toMutableMap()
        profiles[printerKey] = PrinterPaginationProfile(
                plan = profile.plan.normalized(),
                documentation = profile.documentation.normalized(),
        )
        persist(profiles)
    }

    fun reset(printerKey: String) {
        val profiles = configuredProfiles().toMutableMap()
        profiles.remove(printerKey)
        persist(profiles)
    }

    fun remove(printerKey: String) = reset(printerKey)

    private fun configuredProfiles(): LinkedHashMap<String, PrinterPaginationProfile> {
        val raw = preferences.get(PROFILES_KEY, "[]")
        val rows = runCatching { Json.parseToJsonElement(raw) as? JsonArray }.getOrNull().orEmpty()
        return rows.mapNotNull { row ->
            val value = row as? JsonObject ?: return@mapNotNull null
            val key = value.string("printer-key").trim()
            if (key.isBlank()) return@mapNotNull null
            // Retain profiles saved by the first preprint implementation, which only had plan settings.
            val legacyPlan = value.paginationSettings()
            key to PrinterPaginationProfile(
                plan = (value["plan"] as? JsonObject)?.paginationSettings() ?: legacyPlan,
                documentation = (value["documentation"] as? JsonObject)?.documentationSettings()
                    ?: DEFAULT_DOCUMENTATION_SETTINGS,
            )
        }.toMap(LinkedHashMap())
    }

    private fun persist(profiles: Map<String, PrinterPaginationProfile>) {
        val content = buildJsonArray {
            profiles.toSortedMap().forEach { (key, profile) ->
                add(
                    buildJsonObject {
                        put("printer-key", JsonPrimitive(key))
                        put("plan", profile.plan.toJson())
                        put("documentation", profile.documentation.toJson())
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

    private fun JsonObject.paginationSettings(): SheetPaginationSettings = SheetPaginationSettings(
        formatChoice = string("format", "Auto"),
        scaleChoice = string("scale", "1:1"),
        multipage = boolean("multipage", false),
        overlapMm = double("overlap-mm", 5.0),
        pdfRenderMode = enumValue("pdf-render-mode", PdfRenderMode.Rasterized),
    ).normalized()

    private fun SheetPaginationSettings.toJson(): JsonObject = buildJsonObject {
        put("format", JsonPrimitive(formatChoice))
        put("scale", JsonPrimitive(scaleChoice))
        put("multipage", JsonPrimitive(multipage))
        put("overlap-mm", JsonPrimitive(overlapMm))
        put("pdf-render-mode", JsonPrimitive(pdfRenderMode.name))
    }

    private fun DocumentationPrintSettings.normalized(): DocumentationPrintSettings = copy(
        formatChoice = formatChoice.ifBlank { "A4" },
        marginTopMm = marginTopMm.coerceIn(0.0, 80.0),
        marginLeftMm = marginLeftMm.coerceIn(0.0, 80.0),
        marginRightMm = marginRightMm.coerceIn(0.0, 80.0),
        marginBottomMm = marginBottomMm.coerceIn(0.0, 80.0),
    )

    private fun JsonObject.documentationSettings(): DocumentationPrintSettings = DocumentationPrintSettings(
        formatChoice = string("format", "A4"),
        includeHeader = boolean("header", true),
        includeFooter = boolean("footer", true),
        marginTopMm = double("margin-top-mm", 15.0),
        marginLeftMm = double("margin-left-mm", 15.0),
        marginRightMm = double("margin-right-mm", 15.0),
        marginBottomMm = double("margin-bottom-mm", 15.0),
    ).normalized()

    private fun DocumentationPrintSettings.toJson(): JsonObject = buildJsonObject {
        put("format", JsonPrimitive(formatChoice))
        put("header", JsonPrimitive(includeHeader))
        put("footer", JsonPrimitive(includeFooter))
        put("margin-top-mm", JsonPrimitive(marginTopMm))
        put("margin-left-mm", JsonPrimitive(marginLeftMm))
        put("margin-right-mm", JsonPrimitive(marginRightMm))
        put("margin-bottom-mm", JsonPrimitive(marginBottomMm))
    }

    private fun JsonObject.string(name: String, fallback: String = ""): String =
        (this[name] as? JsonPrimitive)?.content ?: fallback

    private fun JsonObject.boolean(name: String, fallback: Boolean): Boolean =
        (this[name] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: fallback

    private fun JsonObject.double(name: String, fallback: Double): Double =
        (this[name] as? JsonPrimitive)?.content?.toDoubleOrNull() ?: fallback

    private inline fun <reified T : Enum<T>> JsonObject.enumValue(name: String, fallback: T): T =
        (this[name] as? JsonPrimitive)?.content
            ?.let { value -> enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) } }
            ?: fallback

    internal companion object {
        const val PREF_NODE = "com/threadwork/app/print"
        const val PROFILES_KEY = "printer-profiles"
        const val SYSTEM_PRINTER_KEY_PREFIX = "system:"
        val DEFAULT_DOCUMENTATION_SETTINGS = DocumentationPrintSettings(
            formatChoice = "A4",
            includeHeader = true,
            includeFooter = true,
            marginTopMm = 15.0,
            marginLeftMm = 15.0,
            marginRightMm = 15.0,
            marginBottomMm = 15.0,
        )

        fun systemPrinterKey(name: String): String = "$SYSTEM_PRINTER_KEY_PREFIX$name"
    }
}
