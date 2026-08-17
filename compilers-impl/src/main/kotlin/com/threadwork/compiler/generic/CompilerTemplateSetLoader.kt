package com.threadwork.compiler.generic

import com.threadwork.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.threadwork.compiler.api.GeneratedElementKind
import com.threadwork.compiler.api.layoutStrategyById
import java.util.Properties

object CompilerTemplateSetLoader {
    fun load(manifestPath: String): CompilerTemplateSet {
        val normalizedManifestPath = manifestPath.ensureAbsoluteResourcePath()
        val properties = Properties().apply {
            resource(normalizedManifestPath).use(::load)
        }
        val directory = normalizedManifestPath.substringBeforeLast('/', "")
        val templates = properties.stringPropertyNames()
            .filter { it.startsWith(TEMPLATE_PREFIX) }
            .associate { key ->
                key.removePrefix(TEMPLATE_PREFIX) to resourceText("$directory/${properties.required(key)}")
            }
        val projectFiles = projectFileIndices(properties).map { index ->
            val prefix = "project.$index."
            TemplateGeneratedFile(
                pathTemplate = properties.required("${prefix}path"),
                contentTemplate = resourceText("$directory/${properties.required("${prefix}content")}"),
                reason = properties.getProperty("${prefix}reason", "Generated from compiler resource template"),
                layoutStrategyIds = properties.csv("${prefix}layouts").toSet(),
                elementKind = properties.getProperty("${prefix}elementKind")
                    ?.let(GeneratedElementKind::valueOf)
                    ?: GeneratedElementKind.ProjectLayout,
            )
        }
        return CompilerTemplateSet(
            templates = templates,
            projectFiles = projectFiles,
            staticFileNames = properties.csv("staticFiles").toSet(),
            fileExtension = properties.getProperty("fileExtension", ""),
            defaultLayoutStrategy = properties.getProperty("defaultLayoutStrategyId")
                ?.let(::layoutStrategyById)
                ?: ClassifiedFilesystemLayoutStrategy,
            emitLinkFiles = properties.getProperty("emitLinkFiles", "true").toBooleanStrict(),
            skipCompilerTemplates = properties.getProperty("skipCompilerTemplates", "false").toBooleanStrict(),
        )
    }

    private fun projectFileIndices(properties: Properties): List<Int> =
        properties.stringPropertyNames()
            .mapNotNull { PROJECT_INDEX.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }
            .distinct()
            .sorted()

    private fun Properties.required(key: String): String =
        requireNotNull(getProperty(key)?.takeIf(String::isNotBlank)) { "Missing compiler template manifest property '$key'." }

    private fun Properties.csv(key: String): List<String> =
        getProperty(key, "").split(',').map(String::trim).filter(String::isNotBlank)

    private fun resourceText(path: String): String =
        resource(path.ensureAbsoluteResourcePath()).bufferedReader().use { it.readText() }

    private fun resource(path: String) =
        requireNotNull(CompilerTemplateSetLoader::class.java.getResourceAsStream(path)) {
            "Compiler template resource '$path' was not found."
        }

    private fun String.ensureAbsoluteResourcePath(): String = if (startsWith('/')) this else "/$this"

    private const val TEMPLATE_PREFIX = "template."
    private val PROJECT_INDEX = Regex("project\\.(\\d+)\\..+")
}
