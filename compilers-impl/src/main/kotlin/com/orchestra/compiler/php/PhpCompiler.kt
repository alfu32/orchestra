package com.orchestra.compiler.php

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.CompilerTemplateSet
import com.orchestra.compiler.generic.CompilerTemplateSetLoader
import com.orchestra.compiler.generic.TemplateSetCompiler
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.validation.DocumentValidator

class PhpCompiler : TemplateSetCompiler() {
    override val id: String = "php-compiler"
    override val displayName: String = "PHP Compiler"
    override val supportedLanguageIds: Set<String> = setOf("php")
    override val supportedTechnologyIds: Set<String> = setOf("php")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("php", "php"))
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun templatesFor(document: InflowDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/php/compiler.properties")
    }
}
