package com.threadwork.app.ui

import com.threadwork.compiler.api.GeneratedFile
import com.threadwork.compiler.api.GeneratedSourceMap
import kotlin.test.Test
import kotlin.test.assertTrue

class QuickJsSourceValidatorTest {
    @Test
    fun `QuickJS reports generated syntax errors without executing source`() {
        val file = GeneratedFile(
            path = "generated.js",
            content = "function main() {\n    const broken = ;\n}",
            originNodeId = null,
            reason = "test",
        )

        val diagnostics = QuickJsSourceValidator.validateDetailed(file)

        assertTrue(diagnostics.isNotEmpty())
        assertTrue(diagnostics.first().diagnostic.message.contains("unexpected token"))
        assertTrue(diagnostics.first().generatedLine == null)
    }

    @Test
    fun `valid JavaScript is accepted without executing it`() {
        val file = GeneratedFile(
            path = "generated.js",
            content = "throw new Error('must not execute');",
            originNodeId = null,
            reason = "test",
        )

        assertTrue(QuickJsSourceValidator.validate(file).isEmpty())
    }
}
