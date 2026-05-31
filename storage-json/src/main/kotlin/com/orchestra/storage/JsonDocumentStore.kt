package com.orchestra.storage

import com.orchestra.core.model.InflowDocument
import com.orchestra.core.validation.DocumentValidator
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json

interface JsonDocumentStore {
    fun save(document: InflowDocument, filePath: Path)
    fun load(filePath: Path): InflowDocument
}

class KotlinxJsonDocumentStore(
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    },
) : JsonDocumentStore {
    override fun save(document: InflowDocument, filePath: Path) {
        filePath.parent?.let(Files::createDirectories)
        Files.writeString(filePath, json.encodeToString(InflowDocument.serializer(), document))
    }

    override fun load(filePath: Path): InflowDocument {
        val document = json.decodeFromString(InflowDocument.serializer(), Files.readString(filePath))
        val errors = DocumentValidator.validate(document).filter { it.severity.name == "Error" }
        require(errors.isEmpty()) {
            errors.joinToString(prefix = "Invalid document:\n", separator = "\n") { "- ${it.message}" }
        }
        return document
    }
}
