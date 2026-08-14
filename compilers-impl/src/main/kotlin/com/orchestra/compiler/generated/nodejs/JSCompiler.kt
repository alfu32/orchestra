package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.CompilerTemplateSet
import com.orchestra.compiler.generic.CompilerTemplateSetLoader
import com.orchestra.compiler.generic.TemplateSetCompiler
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.validation.DocumentValidator

class JSCompiler : TemplateSetCompiler() {
    override val id: String = "nodejs-compiler"
    override val displayName: String = "Node.js CommonJS Compiler"
    override val supportedLanguageIds: Set<String> = setOf("javascript")
    override val supportedTechnologyIds: Set<String> = setOf("nodejs")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("javascript", "nodejs"))
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun templatesFor(document: InflowDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/nodejs/compiler.properties")
    }
}
