package com.threadwork.compiler.php

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

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/php/compiler.properties")
    }
}
