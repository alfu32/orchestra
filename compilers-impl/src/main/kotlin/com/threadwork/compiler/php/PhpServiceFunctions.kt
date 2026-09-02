package com.threadwork.compiler.php

internal data class PhpServiceFunction(
    val name: String,
    val parameters: String,
    val returnType: String,
) {
    val signature: String
        get() = "function $name($parameters)${returnType.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}"

    fun aliasDeclaration(alias: String): String {
        val invocation = "$name(${forwardedArguments()})"
        val statement = if (returnType.trim().lowercase() == "void") "$invocation;" else "return $invocation;"
        return """
            function $alias($parameters)${returnType.takeIf(String::isNotBlank)?.let { ": $it" }.orEmpty()}
            {
                $statement
            }
        """.trimIndent()
    }

    private fun forwardedArguments(): String = splitTopLevel(parameters)
        .mapNotNull { parameter ->
            val variable = VARIABLE_PATTERN.findAll(parameter).lastOrNull() ?: return@mapNotNull null
            val argument = variable.groupValues[1]
            if (parameter.substring(0, variable.range.first).contains("...")) "...$argument" else argument
        }
        .joinToString(", ")

    private fun splitTopLevel(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        val parts = mutableListOf<String>()
        var start = 0
        var depth = 0
        value.forEachIndexed { index, character ->
            when (character) {
                '(', '[', '{' -> depth++
                ')', ']', '}' -> depth = (depth - 1).coerceAtLeast(0)
                ',' -> if (depth == 0) {
                    parts += value.substring(start, index)
                    start = index + 1
                }
            }
        }
        parts += value.substring(start)
        return parts.map(String::trim).filter(String::isNotBlank)
    }

    private companion object {
        val VARIABLE_PATTERN = Regex("(?:&\\s*)?(?:\\.\\.\\.\\s*)?(\\$[A-Za-z_][A-Za-z0-9_]*)")
    }
}

/** Finds user-authored, top-level PHP functions exported by a service library. */
internal object PhpServiceFunctionDiscovery {
    fun discover(source: String): List<PhpServiceFunction> {
        val text = source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, "")
        val functions = linkedMapOf<String, PhpServiceFunction>()
        var statementStart = 0
        var braceDepth = 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> {
                    if (braceDepth == 0) {
                        parseCandidate(text.substring(statementStart, index))?.let { functions[it.name] = it }
                    }
                    braceDepth++
                }

                '}' -> {
                    braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    if (braceDepth == 0) statementStart = index + 1
                }

                ';' -> if (braceDepth == 0) statementStart = index + 1
            }
            index++
        }
        return functions.values.toList()
    }

    private fun parseCandidate(candidate: String): PhpServiceFunction? {
        val normalized = candidate.trim().replace(Regex("\\s+"), " ")
        val match = FUNCTION_PATTERN.find(normalized) ?: return null
        val name = match.groupValues[1]
        val parameters = match.groupValues[2].trim()
        val returnType = match.groupValues[3].trim()
        return PhpServiceFunction(name, parameters, returnType)
    }

    private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
    private val LINE_COMMENT = Regex("(?m)//.*$|#.*$")
    private val FUNCTION_PATTERN = Regex(
        """\bfunction\s+&?\s*([A-Za-z_][A-Za-z0-9_]*)\s*\((.*)\)\s*(?::\s*([^\{]+))?\s*$""",
    )
}
