package com.threadwork.app.editor

import com.threadwork.completion.DeclarationSymbol
import com.threadwork.completion.DeclarationSymbolKind
import com.threadwork.core.model.VOID_LANGUAGE_ID
import java.awt.Color

data class SyntaxToken(
    val start: Int,
    val endExclusive: Int,
    val color: Color,
)

data class RegexLanguageSyntax(
    val id: String,
    val regex: Regex,
    val aliases: Set<String> = emptySet(),
    val color: Color = RegexSyntaxHighlighter.Keyword,
)

object RegexSyntaxHighlighter {
    val Default = Color(0xd4d4d4)
    val Keyword = Color(0xcc7832)
    private val Comment = Color(0x6a9955)
    private val StringLiteral = Color(0xce9178)
    private val NumberLiteral = Color(0xb5cea8)
    private val FunctionSymbol = Color(0xdcdcaa)
    private val TypeSymbol = Color(0x4ec9b0)
    private val ValueSymbol = Color(0x9cdcfe)

    private val stringPattern = Regex(""""(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'""")
    private val blockCommentPattern = Regex("""/\*.*?\*/""")
    private val lineCommentPattern = Regex("""(//|#|--).*$""")
    private val numberPattern = Regex("""\b\d+(?:\.\d+)?\b""")
    private val languages = linkedMapOf<String, RegexLanguageSyntax>()
    private val aliasIndex = linkedMapOf<String, String>()

    init {
        registerBuiltIns()
    }

    fun registerLanguage(syntax: RegexLanguageSyntax) {
        val key = syntax.id.normalizedLanguageKey()
        languages[key] = syntax
        aliasIndex[key] = key
        syntax.aliases.forEach { aliasIndex[it.normalizedLanguageKey()] = key }
    }

    fun availableLanguageIds(): List<String> = languages.values.map { it.id }.sorted()

    fun normalizeLanguage(languageId: String): String =
        aliasIndex[languageId.normalizedLanguageKey()] ?: VOID_LANGUAGE_ID

    fun highlightLine(
        languageId: String,
        line: String,
        declarationSymbols: List<DeclarationSymbol> = emptyList(),
        semanticIdentifierColors: Map<String, Color> = emptyMap(),
    ): List<SyntaxToken> {
        if (line.isEmpty()) return emptyList()
        val tokens = mutableListOf<SyntaxToken>()
        val occupied = BooleanArray(line.length)

        fun add(start: Int, endExclusive: Int, color: Color) {
            if (start !in line.indices || endExclusive <= start) return
            val end = endExclusive.coerceAtMost(line.length)
            if ((start until end).any { occupied[it] }) return
            tokens += SyntaxToken(start, end, color)
            for (index in start until end) occupied[index] = true
        }

        stringPattern.findAll(line).forEach { add(it.range.first, it.range.last + 1, StringLiteral) }
        blockCommentPattern.findAll(line).forEach { add(it.range.first, it.range.last + 1, Comment) }
        lineCommentPattern.find(line)?.let { add(it.range.first, it.range.last + 1, Comment) }
        numberPattern.findAll(line).forEach { add(it.range.first, it.range.last + 1, NumberLiteral) }

        languages[normalizeLanguage(languageId).normalizedLanguageKey()]?.let { syntax ->
            syntax.regex.findAll(line).forEach { match ->
                val range = match.groups.drop(1)
                    .filterNotNull()
                    .filter { it.value.isNotEmpty() }
                    .maxByOrNull { it.range.last - it.range.first }
                    ?.range
                    ?: match.range
                add(range.first, range.last + 1, syntax.color)
            }
        }
        fun addIdentifier(name: String, color: Color) {
            if (name.isBlank()) return
            var start = line.indexOf(name)
            while (start >= 0) {
                val end = start + name.length
                if (line.isIdentifierBoundary(start - 1) && line.isIdentifierBoundary(end)) {
                    add(start, end, color)
                }
                start = line.indexOf(name, start + name.length.coerceAtLeast(1))
            }
        }
        semanticIdentifierColors.entries
            .sortedByDescending { it.key.length }
            .forEach { (name, color) -> addIdentifier(name, color) }
        declarationSymbols.forEach { symbol -> addIdentifier(symbol.name, semanticColor(symbol.kind)) }
        return tokens.sortedBy { it.start }
    }

    private fun semanticColor(kind: DeclarationSymbolKind): Color = when (kind) {
        DeclarationSymbolKind.Function -> FunctionSymbol
        DeclarationSymbolKind.Class,
        DeclarationSymbolKind.Interface,
        DeclarationSymbolKind.Struct,
        DeclarationSymbolKind.Union,
        DeclarationSymbolKind.Enum,
        DeclarationSymbolKind.TypeAlias -> TypeSymbol
        DeclarationSymbolKind.Constant,
        DeclarationSymbolKind.Variable -> ValueSymbol
    }

    private fun String.isIdentifierBoundary(index: Int): Boolean =
        index !in indices || !(this[index].isLetterOrDigit() || this[index] == '_' || this[index] == '$')

    private fun registerBuiltIns() {
        register("c", """(^|\b)(#[a-z]+|auto|break|case|char|const|continue|default|do|double|else|enum|extern|float|for|goto|if|int|long|register|return|short|signed|sizeof|static|struct|switch|typedef|union|unsigned|void|volatile|while)($|\b)""")
        register("cpp", """(^|\b)(class|namespace|template|typename|using|auto|constexpr|virtual|override|public|private|protected|if|else|for|while|return)($|\b)""", setOf("c++", "cc", "cxx", "hpp", "hxx"))
        register("csharp", """(^|\b)(namespace|using|class|struct|interface|enum|public|private|protected|internal|async|await|var|if|else|for|foreach|while|return)($|\b)""", setOf("cs", "c#"))
        register("css", """(^|\b)(color|background|margin|padding|border|display|position|flex|grid|font|width|height)\s*:""")
        register("docker", """(^|\b)(FROM|RUN|CMD|ENTRYPOINT|ENV|ARG|COPY|ADD|EXPOSE|WORKDIR|USER|VOLUME|LABEL)($|\b)""", setOf("dockerfile"))
        register("go", """(^|\b)(package|import|func|type|struct|interface|const|var|if|else|for|range|go|defer|return)($|\b)""")
        register("html", """<\s*/?\s*[A-Za-z][A-Za-z0-9:-]*[^>]*>""", setOf("xml"))
        register("java", """(@[a-zA-Z0-9_]+)|(^|\b)(package|import|class|interface|enum|extends|implements|public|private|protected|static|final|void|int|if|else|for|while|return|try|catch|finally|new|this|super|break|continue)($|\b)""")
        register("javascript", """(@[a-zA-Z0-9_]+)|(^|\b)(function|class|extends|import|from|export|default|var|let|const|if|else|for|while|do|switch|case|break|continue|return|try|catch|finally|new|this|super|async|await|yield)($|\b)""", setOf("js", "node", "nodejs", "01-javascript"))
        register("json", """\{\s*"(?:[^"\\]|\\.)*"\s*:""", setOf("json5", "jsonc", "jsonl"))
        register("kotlin", """(@[a-zA-Z0-9_]+)|(^|\b)(override|operator|public|private|protected|companion|package|import|class|interface|object|data|sealed|enum|fun|val|var|if|else|for|while|when|return|this|super|is|in|as|break|continue|null)($|\b)""", setOf("kt", "kts", "kotlin-jvm", "kotlin-script"))
        register("markdown", """^#{1,6}\s+.+$|^\s*[-*+]|\*\*[^*]+\*\*|\b__[^_]+__($|\b)""", setOf("md", "mdx"))
        register("python", """(@[a-zA-Z0-9_]+)|(^|\b)(import|from|as|class|def|async|await|if|elif|else|for|while|try|except|finally|with|return|yield|lambda|nonlocal|global|pass|break|continue|raise)($|\b)""", setOf("py"))
        register("rust", """(^|\b)(crate|mod|use|pub|fn|struct|enum|trait|impl|type|const|static|let|mut|if|else|match|loop|while|for|in|move|async|await|unsafe|return)($|\b)""", setOf("rs"))
        register("shellscript", """(^|\b)(if|then|elif|else|fi|for|while|until|case|esac|function|select|in|do|done)\b|^\s*#!/bin/(ba|z|k)?sh""", setOf("sh", "bash", "zsh"))
        register("sql", """(^|\b)(SELECT|INSERT|UPDATE|DELETE|FROM|WHERE|JOIN|INNER|LEFT|RIGHT|FULL|ON|GROUP BY|ORDER BY|HAVING|LIMIT|OFFSET|CREATE|ALTER|DROP|TABLE|VIEW|INDEX)($|\b)""")
        register("typescript", """(@[a-zA-Z0-9_]+)|(^|\b)(Input|Output|ViewChild|import|from|export|default|class|extends|implements|interface|type|enum|namespace|module|public|private|protected|readonly|abstract|declare|const|let|var|if|else|for|while|do|switch|case|break|continue|return|try|catch|finally|new|this|super|async|await|yield)($|\b)""", setOf("ts"))
        register("yaml", """^\s*[A-Za-z0-9_-]+\s*:""", setOf("yml"))
    }

    private fun register(id: String, pattern: String, aliases: Set<String> = emptySet()) {
        registerLanguage(
            RegexLanguageSyntax(
                id = id,
                regex = Regex(pattern, setOf(RegexOption.IGNORE_CASE)),
                aliases = aliases,
            ),
        )
    }

    private fun String.normalizedLanguageKey(): String =
        trim().lowercase().removePrefix("language:").removePrefix("lang:")
}
