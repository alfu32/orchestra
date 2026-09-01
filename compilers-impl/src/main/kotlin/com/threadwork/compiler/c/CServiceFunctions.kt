package com.threadwork.compiler.c

internal data class CServiceFunction(
    val returnType: String,
    val name: String,
    val parameters: String,
) {
    val signature: String
        get() = "$returnType $name($parameters)"

    fun pointerDeclaration(alias: String): String =
        "$returnType (*$alias)($parameters) = $name;"
}

/**
 * Extracts the public C functions made available by a service-library node.
 * Explicit prototypes and definitions take precedence over the small built-in
 * header catalogue used for common native APIs.
 */
internal object CServiceFunctionDiscovery {
    fun discover(source: String): List<CServiceFunction> {
        val explicit = parseTopLevelFunctions(source)
        val result = linkedMapOf<String, CServiceFunction>()
        explicit.forEach { result[it.name] = it }
        includedHeaders(source).forEach { header ->
            HEADER_FUNCTIONS[header].orEmpty().forEach { function ->
                result.putIfAbsent(function.name, function)
            }
        }
        return result.values.toList()
    }

    private fun includedHeaders(source: String): List<String> =
        INCLUDE_PATTERN.findAll(source).map { it.groupValues[1].trim() }.toList()

    private fun parseTopLevelFunctions(source: String): List<CServiceFunction> {
        val text = source
            .replace(BLOCK_COMMENT, " ")
            .replace(LINE_COMMENT, "")
            .lineSequence()
            .filterNot { it.trimStart().startsWith('#') }
            .joinToString("\n")
        val functions = mutableListOf<CServiceFunction>()
        var statementStart = 0
        var braceDepth = 0
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> {
                    if (braceDepth == 0) {
                        parseCandidate(text.substring(statementStart, index))?.let(functions::add)
                    }
                    braceDepth++
                }

                '}' -> {
                    braceDepth = (braceDepth - 1).coerceAtLeast(0)
                    if (braceDepth == 0) statementStart = index + 1
                }

                ';' -> if (braceDepth == 0) {
                    parseCandidate(text.substring(statementStart, index))?.let(functions::add)
                    statementStart = index + 1
                }
            }
            index++
        }
        return functions.distinctBy(CServiceFunction::name)
    }

    private fun parseCandidate(candidate: String): CServiceFunction? {
        val normalized = candidate.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank() || normalized.startsWith("typedef ")) return null
        val match = FUNCTION_PATTERN.matchEntire(normalized) ?: return null
        val returnType = match.groupValues[1]
            .replace(STORAGE_CLASS, "")
            .trim()
            .replace(Regex("\\s+"), " ")
        val name = match.groupValues[2]
        val parameters = match.groupValues[3].trim().ifBlank { "void" }
        if (returnType.isBlank() || name in NON_FUNCTION_NAMES) return null
        return CServiceFunction(returnType, name, parameters)
    }

    private val HEADER_FUNCTIONS = mapOf(
        "stdio.h" to listOf(
            CServiceFunction("FILE *", "fopen", "const char *filename, const char *mode"),
            CServiceFunction("int", "fprintf", "FILE *stream, const char *format, ..."),
            CServiceFunction("int", "fclose", "FILE *stream"),
        ),
        "time.h" to listOf(
            CServiceFunction("time_t", "time", "time_t *timer"),
            CServiceFunction("struct tm *", "localtime_r", "const time_t *timer, struct tm *result"),
            CServiceFunction(
                "size_t",
                "strftime",
                "char *destination, size_t capacity, const char *format, const struct tm *time_pointer",
            ),
        ),
        "uuid/uuid.h" to listOf(
            CServiceFunction("void", "uuid_generate_random", "uuid_t output"),
            CServiceFunction("void", "uuid_unparse_lower", "const uuid_t input, char *output"),
        ),
    )
    private val INCLUDE_PATTERN = Regex("(?m)^\\s*#\\s*include\\s*[<\"]([^>\"]+)[>\"]")
    private val BLOCK_COMMENT = Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL))
    private val LINE_COMMENT = Regex("//.*")
    private val FUNCTION_PATTERN = Regex("(.+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\((.*)\\)")
    private val STORAGE_CLASS = Regex("\\b(?:extern|static|inline|_Noreturn)\\b")
    private val NON_FUNCTION_NAMES = setOf("if", "for", "while", "switch", "return", "sizeof")
}
