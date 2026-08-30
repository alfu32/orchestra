package com.threadwork.compiler.naivekotlin

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerTechnology
import com.threadwork.compiler.generic.CompilerTemplateSet
import com.threadwork.compiler.generic.CompilerTemplateSetLoader
import com.threadwork.compiler.generic.TemplateSetCompiler
import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveTechnologyId
import com.threadwork.core.validation.DocumentValidator

class NaiveKotlinCompiler : TemplateSetCompiler() {
    override val id: String = "naive-kotlin"
    override val displayName: String = "Naive Kotlin/JVM Compiler"
    override val supportedLanguageIds: Set<String> = setOf("kotlin")
    override val supportedTechnologyIds: Set<String> = setOf("kotlin-jvm")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("kotlin", "kotlin-jvm"))
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: ThreadworkDocument): Boolean = true

    override fun validate(document: ThreadworkDocument): List<Diagnostic> =
        DocumentValidator.validate(document) + document.nodes.values
            .filter { it.isLink && document.effectiveTechnologyId(it.id) == "kotlin-jvm" }
            .filter { LinkClassifier.classify(document, it) == LinkStereotype.RunnableCapability }
            .map {
                Diagnostic(
                    DiagnosticSeverity.Error,
                    "Kotlin/JVM has no configured runtime compiler for a run capability; use src or a toolchain adapter.",
                    it.id,
                    sourcePluginId = id,
                )
            }

    override fun templatesFor(document: ThreadworkDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/kotlin/compiler.properties")
    }
}
