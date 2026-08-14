package com.orchestra.compiler.naivekotlin

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.CompilerTemplateSet
import com.orchestra.compiler.generic.CompilerTemplateSetLoader
import com.orchestra.compiler.generic.TemplateSetCompiler
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.validation.DocumentValidator

class NaiveKotlinCompiler : TemplateSetCompiler() {
    override val id: String = "naive-kotlin"
    override val displayName: String = "Naive Kotlin/JVM Compiler"
    override val supportedLanguageIds: Set<String> = setOf("kotlin")
    override val supportedTechnologyIds: Set<String> = setOf("kotlin-jvm")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology("kotlin", "kotlin-jvm"))
    override val magicFileNames: Set<String> = TEMPLATES.staticFileNames

    override fun supports(document: InflowDocument): Boolean = true

    override fun validate(document: InflowDocument): List<Diagnostic> =
        DocumentValidator.validate(document)

    override fun templatesFor(document: InflowDocument, options: CompilerOptions): CompilerTemplateSet =
        TEMPLATES

    private companion object {
        val TEMPLATES = CompilerTemplateSetLoader.load("/compiler-templates/kotlin/compiler.properties")
    }
}
