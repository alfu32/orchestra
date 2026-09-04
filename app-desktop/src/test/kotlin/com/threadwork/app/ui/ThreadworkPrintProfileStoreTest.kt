package com.threadwork.app.ui

import java.util.prefs.Preferences
import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadworkPrintProfileStoreTest {
    @Test
    fun `persists searchable plan rendering mode`() {
        val preferences = Preferences.userRoot().node("threadwork-test-${System.nanoTime()}")
        try {
            val store = ThreadworkPrintProfileStore(preferences)
            val fallbackPlan = SheetPaginationSettings("Auto", "1:1", false, 5.0)
            val fallbackDocumentation = ThreadworkPrintProfileStore.DEFAULT_DOCUMENTATION_SETTINGS
            store.save(
                PrinterTarget.PDF_PRINTER_KEY,
                PrinterPaginationProfile(
                    plan = fallbackPlan.copy(pdfRenderMode = PdfRenderMode.Searchable),
                    documentation = fallbackDocumentation,
                ),
            )

            assertEquals(
                PdfRenderMode.Searchable,
                store.profileFor(
                    PrinterTarget.PDF_PRINTER_KEY,
                    fallbackPlan,
                    fallbackDocumentation,
                ).plan.pdfRenderMode,
            )
        } finally {
            preferences.removeNode()
        }
    }
}
