package com.threadwork.compiler.php

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerCodeIntelligence
import com.threadwork.compiler.api.CompilerCodeSymbol
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.api.compilerArgumentName
import com.threadwork.compiler.api.defaultCodeIntelligence
import com.threadwork.compiler.generic.CompilerTemplateSet
import com.threadwork.compiler.generic.CompilerTemplateSetLoader
import com.threadwork.compiler.generic.TemplateSetCompiler
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.classification.NodeStereotype
import com.threadwork.core.classification.stereotype
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.validation.DocumentValidator

class PhpCompiler : TemplateSetCompiler() {
    override val id: String = "php-compiler"
    override val displayName: String = "PHP Compiler"
    override val supportedLanguageIds: Set<String> = setOf("php")
    override val supportedTechnologyIds: Set<String> = setOf("php")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("php", "php"))
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document) + document.nodes.values
            .filter { it.isLink && document.effectiveTechnologyId(it.id) == "php" }
            .filter { LinkClassifier.classify(document, it) == LinkStereotype.RunnableCapability }
            .map {
                Diagnostic(
                    DiagnosticSeverity.Error,
                    "PHP runtime compilation is not enabled for a run capability; use src or an explicit evaluator adapter.",
                    it.id,
                    sourcePluginId = id,
                )
            }

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

    override fun codeIntelligence(document: ThreadworkDocument, node: Node): CompilerCodeIntelligence {
        val defaults = defaultCodeIntelligence(document, node)
        val phpScopeSymbols = defaults.symbols.map(::toPhpScopeSymbol)
        return defaults.copy(
            symbols = (phpScopeSymbols + runtimeSymbols).distinctBy { it.name to it.kind },
        )
    }

    override fun generatedFunctionHeader(
        document: ThreadworkDocument,
        node: Node,
        section: NodeTextSection,
    ): String {
        if (node.isLink || node.stereotype(document) == NodeStereotype.ServiceLibrary) return ""
        val functionPrefix = when (section) {
            NodeTextSection.Declaration -> "run"
            NodeTextSection.Instantiation -> "init"
            else -> return ""
        }
        val arguments = mutableListOf("array &\$context")
        node.incomingLinks.mapNotNull(document.nodes::get).forEach { linkNode ->
            val argument = "\$${compilerArgumentName(linkNode.name)}"
            if (LinkClassifier.isCapability(document, linkNode)) {
                arguments += "mixed $argument"
            } else {
                arguments += "array &$argument"
            }
        }
        node.outgoingLinks.mapNotNull(document.nodes::get)
            .filterNot { LinkClassifier.isCapability(document, it) }
            .forEach { linkNode ->
                arguments += "array &\$${compilerArgumentName(linkNode.name)}"
            }
        return "function ${functionPrefix}_${indexedNodeSymbol(document, node)}(${arguments.joinToString(", ")}): void {"
    }

    private fun toPhpScopeSymbol(symbol: CompilerCodeSymbol): CompilerCodeSymbol {
        if (symbol.kind !in PHP_VARIABLE_SYMBOL_KINDS) return symbol
        val phpName = "\$${symbol.name.removePrefix("$")}"
        return symbol.copy(
            name = phpName,
            members = symbol.members.map { member ->
                val memberName = when (symbol.kind) {
                    CompilerCodeSymbolKind.SourceCapability,
                    CompilerCodeSymbolKind.RunnableCapability -> member.name
                        .replaceFirst(symbol.name, phpName)
                        .replace(".", "->")
                    else -> member.name
                }
                member.copy(name = memberName)
            },
        )
    }

    private fun indexedNodeSymbol(document: ThreadworkDocument, node: Node): String {
        val nodes = document.nodes.values.filterNot(Node::isLink).sortedBy { it.id.value }
        val index = nodes.indexOfFirst { it.id == node.id }.takeIf { it >= 0 }?.plus(1) ?: 1
        return "${safeIdentifier(node.name)}_$index"
    }

    private fun safeIdentifier(value: String): String {
        val modelName = value.trim()
        val sanitized = if (PHP_IDENTIFIER.matches(modelName)) {
            modelName
        } else {
            modelName.replace(Regex("[^A-Za-z0-9_]+"), "_").trim('_')
        }.ifBlank { "node" }.lowercase()
        return if (sanitized.first().isDigit()) "_$sanitized" else sanitized
    }

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/php/compiler.properties")
        val PHP_VARIABLE_SYMBOL_KINDS = setOf(
            CompilerCodeSymbolKind.InputBuffer,
            CompilerCodeSymbolKind.OutputBuffer,
            CompilerCodeSymbolKind.ServiceInstance,
            CompilerCodeSymbolKind.SourceCapability,
            CompilerCodeSymbolKind.RunnableCapability,
        )
        val runtimeSymbols = listOf(
            CompilerCodeSymbol(
                name = "\$context",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "array",
                detail = "PHP runtime context",
                documentation = "Execution context supplied to every generated PHP node function.",
            ),
            CompilerCodeSymbol(
                name = "\$GLOBALS['threadwork_running']",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "bool",
                detail = "PHP runtime ingress flag",
                documentation = "Set to false by SIGINT, SIGTERM, or threadwork_shutdown_request(). PHP generator nodes return before producing new packets when this flag is false.",
            ),
            CompilerCodeSymbol(
                name = "\$GLOBALS['threadwork_transit']",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "int",
                detail = "PHP runtime completed transport count",
                documentation = "Incremented after each non-empty modeled data-link transport completes.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_shutdown_request",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_shutdown_request()",
                detail = "close generator ingress",
                documentation = "Requests a graceful shutdown while processors continue draining modeled links.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_record_transit",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_record_transit()",
                detail = "record completed link transport",
                documentation = "Increments the runtime transport counter after a packet reaches the target link buffer.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_network_shutdown_begin",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_network_shutdown_begin(int \$idleTicks)",
                detail = "begin network drain monitoring",
                documentation = "Begins a bounded idle-window check for residual modeled link traffic.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_network_has_recent_transit",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "bool threadwork_network_has_recent_transit()",
                detail = "continue network drain while traffic is recent",
                documentation = "Returns true while modeled link transports are still active or the configured idle window has not elapsed.",
            ),
            CompilerCodeSymbol(
                name = "threadwork_install_shutdown_handlers",
                kind = CompilerCodeSymbolKind.RuntimeSymbol,
                typeName = "void threadwork_install_shutdown_handlers()",
                detail = "install SIGINT and SIGTERM shutdown handling",
                documentation = "Installs PHP PCNTL signal handlers when the extension is available; the generated runtime remains valid when it is unavailable.",
            ),
        )
        val PHP_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}
