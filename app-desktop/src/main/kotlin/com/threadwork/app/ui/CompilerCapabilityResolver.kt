package com.threadwork.app.ui

import com.threadwork.compiler.api.ANY_LANGUAGE_ID
import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerPlugin
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.model.effectiveLayoutStrategyId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.rootNode

internal class CompilerCapabilityResolver(
    private val compilers: List<CompilerPlugin>,
) {
    fun compilerFor(document: ThreadworkDocument, nodeId: NodeId): CompilerPlugin? {
        val supporting = compilers.filter { compiler ->
            runCatching { compiler.supports(document) }.getOrDefault(false)
        }
        if (supporting.isEmpty()) return null

        val root = document.rootNode()
        val rootCompiler = supporting.firstOrNull { it.id == root.technology.compilerId.trim() }
            ?: compilerForTechnology(
                supporting,
                document.effectiveTechnologyId(root.id),
                document.effectiveLanguageId(root.id),
            )
            ?: supporting.first()

        val technologyId = document.effectiveTechnologyId(nodeId).trim()
        val languageId = document.effectiveLanguageId(nodeId).trim()
        if (technologyId.isBlank() || compilerSupports(rootCompiler, technologyId, languageId)) {
            return literalFileCompiler(document, nodeId, supporting) ?: rootCompiler
        }

        return compilerForTechnology(
            supporting.filterNot { it.id == rootCompiler.id },
            technologyId,
            languageId,
        ) ?: literalFileCompiler(document, nodeId, supporting) ?: rootCompiler
    }

    fun supportedLayoutStrategyIds(document: ThreadworkDocument, nodeId: NodeId): Set<String> {
        if (literalFileCompiler(document, nodeId, compilers) != null) {
            return setOf(SingleFileLayoutStrategy.id)
        }
        val compiler = compilerFor(document, nodeId) ?: return emptySet()
        return compiler.supportedLayoutStrategyIds.ifEmpty {
            setOf(compiler.layoutStrategy(CompilerOptions()).id)
        }
    }

    private fun compilerForTechnology(
        candidates: List<CompilerPlugin>,
        technologyId: String,
        languageId: String,
    ): CompilerPlugin? {
        if (technologyId.isBlank()) return null
        return candidates
            .asSequence()
            .filter { compilerSupports(it, technologyId, languageId) }
            .sortedWith(
                compareByDescending<CompilerPlugin> { compiler ->
                    compiler.providedTechnologies.any { technology ->
                        technology.technologyId == technologyId &&
                            (technology.languageId == languageId || technology.languageId == ANY_LANGUAGE_ID)
                    }
                }.thenBy { it.id },
            )
            .firstOrNull()
    }

    /** A source leaf with no technology compiler is a literal single-file artifact. */
    private fun literalFileCompiler(
        document: ThreadworkDocument,
        nodeId: NodeId,
        candidates: List<CompilerPlugin>,
    ): CompilerPlugin? {
        val node = document.getElementById(nodeId) ?: return null
        if (node.isLink || node.children.isNotEmpty()) return null
        if (document.effectiveLayoutStrategyId(nodeId) != SingleFileLayoutStrategy.id) return null
        val technologyId = document.effectiveTechnologyId(nodeId).trim()
        val languageId = document.effectiveLanguageId(nodeId).trim()
        val hasDedicatedCompiler = compilerForTechnology(
            candidates.filterNot { it.id == "filesystem" },
            technologyId,
            languageId,
        ) != null
        return if (hasDedicatedCompiler) null else candidates.firstOrNull { it.id == "filesystem" }
    }

    private fun compilerSupports(
        compiler: CompilerPlugin,
        technologyId: String,
        languageId: String,
    ): Boolean =
        technologyId in compiler.supportedTechnologyIds ||
            compiler.providedTechnologies.any { technology ->
                technology.technologyId == technologyId &&
                    (languageId.isBlank() ||
                        technology.languageId == languageId ||
                        technology.languageId == ANY_LANGUAGE_ID)
            }
}
