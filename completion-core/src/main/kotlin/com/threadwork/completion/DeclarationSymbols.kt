package com.threadwork.completion

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveTextLanguageId

enum class DeclarationSymbolKind {
    Function,
    Class,
    Interface,
    Struct,
    Union,
    Enum,
    TypeAlias,
    Constant,
    Variable,
}

data class DeclarationSymbol(
    val name: String,
    val kind: DeclarationSymbolKind,
    val header: String,
    val languageId: String,
    val ownerNodeId: NodeId,
    val ownerNodeName: String,
    val startOffset: Int,
    val endOffset: Int,
)

/** Language-specific declaration discovery. Parser-backed implementations can replace the built-ins. */
interface DeclarationSymbolExtractor {
    fun supports(languageId: String): Boolean

    fun extract(
        source: String,
        languageId: String,
        ownerNodeId: NodeId,
        ownerNodeName: String,
    ): List<DeclarationSymbol>
}

class DocumentDeclarationSymbolIndex(
    private val extractors: List<DeclarationSymbolExtractor> = BuiltInDeclarationSymbolExtractors.all,
) {
    fun symbols(document: ThreadworkDocument, request: CompletionRequest): List<DeclarationSymbol> {
        val requestedLanguage = normalizedLanguageId(request.languageId)
        if (requestedLanguage.isBlank() || requestedLanguage == "plain") return emptyList()
        if (request.textSection != NodeTextSection.Declaration) return emptyList()

        val node = document.nodes[request.nodeId] ?: return emptyList()
        val languageId = document.effectiveTextLanguageId(node.id, NodeTextSection.Declaration)
        if (normalizedLanguageId(languageId) != requestedLanguage) return emptyList()
        if (request.fullText.isBlank()) return emptyList()

        return extractors.firstOrNull { it.supports(languageId) }
            ?.extract(request.fullText, languageId, node.id, node.name)
            .orEmpty()
            .distinctBy { listOf(it.languageId, it.kind.name, it.name, it.header) }
    }
}

object BuiltInDeclarationSymbolExtractors {
    val all: List<DeclarationSymbolExtractor> = listOf(
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("c"),
            patterns = listOf(
                declarationPattern(DeclarationSymbolKind.Struct, """(?m)^[\t ]*(?:typedef[\t ]+)?struct[\t ]+(?<name>[A-Za-z_]\w*)[^;{]*(?:\{|;)"""),
                declarationPattern(DeclarationSymbolKind.Union, """(?m)^[\t ]*(?:typedef[\t ]+)?union[\t ]+(?<name>[A-Za-z_]\w*)[^;{]*(?:\{|;)"""),
                declarationPattern(DeclarationSymbolKind.Enum, """(?m)^[\t ]*(?:typedef[\t ]+)?enum[\t ]+(?<name>[A-Za-z_]\w*)[^;{]*(?:\{|;)"""),
                declarationPattern(DeclarationSymbolKind.TypeAlias, """(?m)^[\t ]*typedef[\t ]+(?!(?:struct|union|enum)\b)[^;{}]+?[\t *]+(?<name>[A-Za-z_]\w*)[\t ]*;"""),
                declarationPattern(
                    DeclarationSymbolKind.Function,
                    """(?m)^[\t ]*(?!if\b|for\b|while\b|switch\b|return\b)(?:(?:extern|static|inline|const|volatile|unsigned|signed|long|short|register|_Noreturn)[\t ]+)*(?:(?:struct|union|enum)[\t ]+[A-Za-z_]\w*|[A-Za-z_]\w*)(?:[\t ]+|[\t ]*\*+[\t ]*)(?:[A-Za-z_]\w*[\t *]+)*(?<name>[A-Za-z_]\w*)[\t ]*\([^;{}]*\)[\t ]*(?:;|\{)""",
                ),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("cpp", "c++", "cc", "cxx", "hpp", "hxx"),
            patterns = listOf(
                typedHeaderPattern("class", DeclarationSymbolKind.Class),
                typedHeaderPattern("struct", DeclarationSymbolKind.Struct),
                typedHeaderPattern("union", DeclarationSymbolKind.Union),
                typedHeaderPattern("enum(?:[\t ]+class)?", DeclarationSymbolKind.Enum),
                declarationPattern(DeclarationSymbolKind.TypeAlias, """(?m)^[\t ]*(?:using[\t ]+(?<name>[A-Za-z_]\w*)[\t ]*=|typedef[\t ]+[^;]+[\t *]+(?<alias>[A-Za-z_]\w*))[\t ]*;""", "name", "alias"),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:(?:template[\t ]*<[^>]+>|public:|private:|protected:|static|inline|virtual|constexpr|const|unsigned|signed|long|short)[\t ]+)*(?:[A-Za-z_:~][\w:<>~]*[\t *&]+)+(?<name>[A-Za-z_~]\w*)[\t ]*\([^;{}]*\)(?:[\t ]*(?:const|override|final|noexcept))*[\t ]*(?:;|\{)"""),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("kotlin", "kt", "kts", "kotlin-jvm", "kotlin-script"),
            patterns = listOf(
                declarationPattern(DeclarationSymbolKind.Class, """(?m)^[\t ]*(?:(?:public|private|protected|internal|data|sealed|open|abstract|value|annotation)[\t ]+)*(?:class|object)[\t ]+(?<name>[A-Za-z_]\w*)[^\n{]*(?:\{|$)"""),
                declarationPattern(DeclarationSymbolKind.Interface, """(?m)^[\t ]*(?:(?:public|private|protected|internal|sealed|fun)[\t ]+)*interface[\t ]+(?<name>[A-Za-z_]\w*)[^\n{]*(?:\{|$)"""),
                declarationPattern(DeclarationSymbolKind.Enum, """(?m)^[\t ]*(?:(?:public|private|protected|internal)[\t ]+)*enum[\t ]+class[\t ]+(?<name>[A-Za-z_]\w*)[^\n{]*(?:\{|$)"""),
                declarationPattern(DeclarationSymbolKind.TypeAlias, """(?m)^[\t ]*(?:(?:public|private|protected|internal)[\t ]+)*typealias[\t ]+(?<name>[A-Za-z_]\w*)[\t ]*=[^\n]+"""),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:(?:public|private|protected|internal|override|operator|inline|infix|tailrec|suspend|external)[\t ]+)*fun(?:[\t ]+<[^>]+>)?[\t ]+(?:[A-Za-z_][\w.<>?]*\.)?(?<name>[A-Za-z_]\w*)[\t ]*\([^)]*\)(?:[\t ]*:[\t ]*[^={\n]+)?"""),
                declarationPattern(DeclarationSymbolKind.Constant, """(?m)^[\t ]*(?:const[\t ]+)?val[\t ]+(?<name>[A-Za-z_]\w*)[\t ]*(?::[^=\n]+)?="""),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("javascript", "js", "node", "nodejs", "01-javascript", "typescript", "ts"),
            patterns = listOf(
                declarationPattern(DeclarationSymbolKind.Class, """(?m)^[\t ]*(?:export[\t ]+)?(?:default[\t ]+)?class[\t ]+(?<name>[A-Za-z_$][\w$]*)[^\n{]*(?:\{|$)"""),
                declarationPattern(DeclarationSymbolKind.Interface, """(?m)^[\t ]*(?:export[\t ]+)?interface[\t ]+(?<name>[A-Za-z_$][\w$]*)[^\n{]*(?:\{|$)"""),
                declarationPattern(DeclarationSymbolKind.TypeAlias, """(?m)^[\t ]*(?:export[\t ]+)?type[\t ]+(?<name>[A-Za-z_$][\w$]*)[\t ]*=[^;\n]+"""),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:export[\t ]+)?(?:default[\t ]+)?(?:async[\t ]+)?function\*?[\t ]+(?<name>[A-Za-z_$][\w$]*)[\t ]*\([^)]*\)"""),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:export[\t ]+)?(?:const|let|var)[\t ]+(?<name>[A-Za-z_$][\w$]*)[\t ]*=[\t ]*(?:async[\t ]*)?(?:\([^)]*\)|[A-Za-z_$][\w$]*)[\t ]*=>"""),
                declarationPattern(DeclarationSymbolKind.Variable, """(?m)^[\t ]*(?:export[\t ]+)?(?:const|let|var)[\t ]+(?<name>[A-Za-z_$][\w$]*)[\t ]*="""),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("php"),
            patterns = listOf(
                typedHeaderPattern("class", DeclarationSymbolKind.Class, phpModifiers),
                typedHeaderPattern("interface", DeclarationSymbolKind.Interface, phpModifiers),
                typedHeaderPattern("trait", DeclarationSymbolKind.Interface, phpModifiers),
                typedHeaderPattern("enum", DeclarationSymbolKind.Enum, phpModifiers),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:(?:public|private|protected|static|final|abstract|readonly)[\t ]+)*function[\t ]+&?[\t ]*(?<name>[A-Za-z_]\w*)[\t ]*\([^)]*\)(?:[\t ]*:[\t ]*[^\n{;]+)?"""),
                declarationPattern(DeclarationSymbolKind.Constant, """(?m)^[\t ]*(?:(?:public|private|protected|final)[\t ]+)*const[\t ]+(?<name>[A-Za-z_]\w*)[\t ]*="""),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("java"),
            patterns = listOf(
                typedHeaderPattern("class", DeclarationSymbolKind.Class, jvmModifiers),
                typedHeaderPattern("interface", DeclarationSymbolKind.Interface, jvmModifiers),
                typedHeaderPattern("enum", DeclarationSymbolKind.Enum, jvmModifiers),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:(?:public|private|protected|static|final|abstract|synchronized|native|default)[\t ]+)+(?:[A-Za-z_][\w<>?,.\[\]]*[\t ]+)(?<name>[A-Za-z_]\w*)[\t ]*\([^)]*\)(?:[\t ]+throws[\t ]+[^\n{]+)?"""),
            ),
        ),
        RegexDeclarationSymbolExtractor(
            languageIds = setOf("python", "py"),
            patterns = listOf(
                declarationPattern(DeclarationSymbolKind.Class, """(?m)^[\t ]*class[\t ]+(?<name>[A-Za-z_]\w*)[^:\n]*:"""),
                declarationPattern(DeclarationSymbolKind.Function, """(?m)^[\t ]*(?:async[\t ]+)?def[\t ]+(?<name>[A-Za-z_]\w*)[\t ]*\([^)]*\)(?:[\t ]*->[\t ]*[^:\n]+)?[\t ]*:"""),
            ),
        ),
    )

    private const val phpModifiers = "(?:(?:abstract|final|readonly)[\\t ]+)*"
    private const val jvmModifiers = "(?:(?:public|private|protected|static|final|abstract|sealed|non-sealed|strictfp)[\\t ]+)*"

    private fun typedHeaderPattern(
        keyword: String,
        kind: DeclarationSymbolKind,
        modifiers: String = "",
    ): DeclarationPattern = declarationPattern(
        kind,
        """(?m)^[\t ]*$modifiers$keyword[\t ]+(?<name>[A-Za-z_]\w*)[^\n{]*(?:\{|$)""",
    )
}

private data class DeclarationPattern(
    val kind: DeclarationSymbolKind,
    val regex: Regex,
    val nameGroups: List<String>,
)

private fun declarationPattern(
    kind: DeclarationSymbolKind,
    pattern: String,
): DeclarationPattern = DeclarationPattern(kind, Regex(pattern), listOf("name"))

private fun declarationPattern(
    kind: DeclarationSymbolKind,
    pattern: String,
    firstNameGroup: String,
    vararg additionalNameGroups: String,
): DeclarationPattern = DeclarationPattern(
    kind,
    Regex(pattern),
    listOf(firstNameGroup) + additionalNameGroups,
)

private class RegexDeclarationSymbolExtractor(
    languageIds: Set<String>,
    private val patterns: List<DeclarationPattern>,
) : DeclarationSymbolExtractor {
    private val normalizedLanguageIds = languageIds.mapTo(linkedSetOf(), ::normalizedLanguageId)

    override fun supports(languageId: String): Boolean = normalizedLanguageId(languageId) in normalizedLanguageIds

    override fun extract(
        source: String,
        languageId: String,
        ownerNodeId: NodeId,
        ownerNodeName: String,
    ): List<DeclarationSymbol> {
        val searchable = maskCommentsAndLiterals(source)
        return patterns.flatMap { pattern ->
            pattern.regex.findAll(searchable).mapNotNull { match ->
                val name = pattern.nameGroups.firstNotNullOfOrNull { group ->
                    match.groups[group]?.value?.takeIf(String::isNotBlank)
                } ?: return@mapNotNull null
                val originalHeader = source.substring(match.range).trim()
                DeclarationSymbol(
                    name = name,
                    kind = pattern.kind,
                    header = originalHeader.toDeclarationHeader(),
                    languageId = normalizedLanguageId(languageId),
                    ownerNodeId = ownerNodeId,
                    ownerNodeName = ownerNodeName,
                    startOffset = match.range.first,
                    endOffset = match.range.last + 1,
                )
            }.toList()
        }.distinctBy { Triple(it.kind, it.name, it.header) }
    }
}

private fun String.toDeclarationHeader(): String =
    trim().removeSuffix("{").removeSuffix(";").trim().replace(Regex("\\s+"), " ")

private fun normalizedLanguageId(languageId: String): String =
    when (val key = languageId.trim().lowercase().removePrefix("language:").removePrefix("lang:")) {
        "js", "node", "nodejs", "01-javascript" -> "javascript"
        "ts" -> "typescript"
        "kt", "kts", "kotlin-jvm", "kotlin-script" -> "kotlin"
        "c++", "cc", "cxx", "hpp", "hxx" -> "cpp"
        "py" -> "python"
        else -> key
    }

private fun maskCommentsAndLiterals(source: String): String {
    val result = source.toCharArray()
    var index = 0
    var quote: Char? = null
    var lineComment = false
    var blockComment = false
    while (index < result.size) {
        val current = source[index]
        val next = source.getOrNull(index + 1)
        when {
            lineComment -> {
                if (current == '\n') {
                    lineComment = false
                } else {
                    result[index] = ' '
                }
            }
            blockComment -> {
                result[index] = if (current == '\n') '\n' else ' '
                if (current == '*' && next == '/') {
                    result[index + 1] = ' '
                    index++
                    blockComment = false
                }
            }
            quote != null -> {
                result[index] = if (current == '\n') '\n' else ' '
                if (current == '\\' && next != null) {
                    result[index + 1] = if (next == '\n') '\n' else ' '
                    index++
                } else if (current == quote) {
                    quote = null
                }
            }
            current == '/' && next == '/' -> {
                result[index] = ' '
                result[index + 1] = ' '
                index++
                lineComment = true
            }
            current == '/' && next == '*' -> {
                result[index] = ' '
                result[index + 1] = ' '
                index++
                blockComment = true
            }
            current == '"' || current == '\'' || current == '`' -> {
                result[index] = ' '
                quote = current
            }
        }
        index++
    }
    return result.concatToString()
}
