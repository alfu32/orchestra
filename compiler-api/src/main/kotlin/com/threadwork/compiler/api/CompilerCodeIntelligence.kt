package com.threadwork.compiler.api

import com.threadwork.core.classification.LinkClassifier
import com.threadwork.core.classification.LinkStereotype
import com.threadwork.core.model.BuiltInTypeIds
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.effectiveLanguageId
import com.threadwork.core.model.getElementById
import com.threadwork.core.model.linkTypeDisplayName
import com.threadwork.core.model.typeDisplayName

/**
 * Compiler-owned names and members made available to an entity editor.  A compiler
 * may override [CompilerPlugin.codeIntelligence] for a runtime with richer APIs;
 * the default covers Threadwork's generated buffer and dependency conventions.
 */
enum class CompilerCodeSymbolKind {
    InputBuffer,
    OutputBuffer,
    ServiceInstance,
    LibraryFunction,
    Type,
    TypeMember,
    BufferMember,
    SourceCapability,
    RunnableCapability,
    GeneratedFunction,
    RuntimeSymbol,
}

data class CompilerCodeMember(
    val name: String,
    val detail: String = "",
    val documentation: String = "",
)

data class CompilerCodeSymbol(
    val name: String,
    val kind: CompilerCodeSymbolKind,
    val typeName: String = "",
    val detail: String = "",
    val documentation: String = "",
    val members: List<CompilerCodeMember> = emptyList(),
    /** Model entity that introduced this symbol, normally a connected link. */
    val originNodeId: NodeId? = null,
)

data class CompilerTypeFieldInfo(
    val name: String,
    val typeName: String,
    val isReference: Boolean,
)

data class CompilerTypeInformation(
    val name: String,
    val languageId: String,
    val declaration: String,
    val documentation: String = "",
    val fields: List<CompilerTypeFieldInfo> = emptyList(),
)

data class CompilerCodeIntelligence(
    val symbols: List<CompilerCodeSymbol> = emptyList(),
    val types: List<CompilerTypeInformation> = emptyList(),
)

/**
 * Processor-local argument name. Valid identifier spelling from the model is
 * preserved; only characters that cannot occur in an identifier are replaced.
 * Link and service allocations may be indexed, but this argument is not.
 */
fun compilerArgumentName(value: String): String {
    val modelName = value.trim()
    if (Regex("[A-Za-z_][A-Za-z0-9_]*").matches(modelName)) return modelName
    val candidate = modelName.replace(Regex("[^A-Za-z0-9_]+"), "_")
        .trim('_')
        .ifBlank { "value" }
    return if (candidate.first().isDigit()) "_$candidate" else candidate
}

fun defaultCodeIntelligence(
    document: ThreadworkDocument,
    node: Node,
): CompilerCodeIntelligence {
    val languageId = document.effectiveLanguageId(node.id)
    val typeInfos = linkedMapOf<String, CompilerTypeInformation>()
    val symbols = mutableListOf<CompilerCodeSymbol>()

    fun registerType(linkNode: Node): CompilerTypeInformation? {
        val link = linkNode.link ?: return null
        val typeName = document.linkTypeDisplayName(linkNode).ifBlank { "Any" }
        val typeNode = link.typeDefinitionId.takeIf(String::isNotBlank)?.let(document::getElementById)
        val fields = typeNode?.typeDefinition?.fields.orEmpty().map { field ->
            CompilerTypeFieldInfo(
                name = field.name,
                typeName = document.typeDisplayName(field.typeId),
                isReference = field.isReference,
            )
        }
        val declaration = typeNode?.text?.declaration?.trim().orEmpty()
            .ifBlank { link.payloadDefinition.trim() }
            .ifBlank { builtInTypeDescription(typeName, languageId) }
        val info = CompilerTypeInformation(
            name = typeName,
            languageId = languageId,
            declaration = declaration,
            documentation = "Type carried by link '${linkNode.name}'.",
            fields = fields,
        )
        typeInfos.putIfAbsent(typeName, info)
        return typeInfos[typeName]
    }

    fun addDataLink(linkNode: Node, input: Boolean) {
        val type = registerType(linkNode) ?: return
        val argumentName = compilerArgumentName(linkNode.name)
        val bufferMethods = bufferMethodsFor(languageId, argumentName, input)
        val itemMembers = type.fields.map { field ->
            CompilerCodeMember(
                name = "$argumentName.${field.name}",
                detail = "${field.typeName}${if (field.isReference) " reference" else ""}",
                documentation = "Field '${field.name}' of ${type.name}.",
            )
        }
        symbols += CompilerCodeSymbol(
            name = argumentName,
            kind = if (input) CompilerCodeSymbolKind.InputBuffer else CompilerCodeSymbolKind.OutputBuffer,
            typeName = type.name,
            detail = "${if (input) "input" else "output"} buffer of ${type.name}",
            documentation = "Processor-local ${if (input) "input" else "output"} buffer for link '${linkNode.name}'.",
            members = bufferMethods + itemMembers,
            originNodeId = linkNode.id,
        )
        symbols += CompilerCodeSymbol(
            name = type.name,
            kind = CompilerCodeSymbolKind.Type,
            typeName = type.name,
            detail = "link item type",
            documentation = type.declaration,
            members = type.fields.map { field ->
                CompilerCodeMember(
                    name = "${type.name}.${field.name}",
                    detail = field.typeName,
                    documentation = "Field '${field.name}' of ${type.name}.",
                )
            },
        )
    }

    node.incomingLinks.mapNotNull(document::getElementById).forEach { linkNode ->
        when (LinkClassifier.classify(document, linkNode)) {
            LinkStereotype.UsageImport,
            LinkStereotype.DependencyInjection -> {
                val source = linkNode.link?.sourceNodeId?.let(document::getElementById)
                val localName = compilerArgumentName(linkNode.name)
                symbols += CompilerCodeSymbol(
                    name = localName,
                    kind = CompilerCodeSymbolKind.ServiceInstance,
                    typeName = source?.name.orEmpty(),
                    detail = "service instance${source?.name?.let { " of $it" }.orEmpty()}",
                    documentation = "Execution-context service supplied by dependency link '${linkNode.name}'.",
                    originNodeId = linkNode.id,
                )
            }

            LinkStereotype.SourceCapability,
            LinkStereotype.RunnableCapability -> {
                val stereotype = LinkClassifier.classify(document, linkNode)
                val source = linkNode.link?.sourceNodeId?.let(document::getElementById)
                val localName = compilerArgumentName(linkNode.name)
                val sourceCapability = stereotype == LinkStereotype.SourceCapability
                val method = if (sourceCapability) "getSource" else "getRunnable"
                val product = if (sourceCapability) "source product" else "runnable product"
                symbols += CompilerCodeSymbol(
                    name = localName,
                    kind = if (sourceCapability) {
                        CompilerCodeSymbolKind.SourceCapability
                    } else {
                        CompilerCodeSymbolKind.RunnableCapability
                    },
                    typeName = "${source?.name.orEmpty()}${if (sourceCapability) "Source" else "Runnable"}Capability",
                    detail = "$product provider${source?.name?.let { " for $it" }.orEmpty()}",
                    documentation = "Synchronous compiler capability supplied by link '${linkNode.name}'.",
                    members = listOf(
                        CompilerCodeMember(
                            name = "$localName.$method(parameters)",
                            detail = "returns $product",
                            documentation = "Build a customized $product from consumer-controlled parameters.",
                        ),
                    ),
                    originNodeId = linkNode.id,
                )
            }

            else -> addDataLink(linkNode, input = true)
        }
    }
    node.outgoingLinks.mapNotNull(document::getElementById).forEach { linkNode ->
        if (!LinkClassifier.isCapability(document, linkNode)) {
            addDataLink(linkNode, input = false)
        }
    }

    return CompilerCodeIntelligence(
        symbols = symbols.distinctBy { it.name to it.kind },
        types = typeInfos.values.toList(),
    )
}

fun defaultTypeInformation(
    document: ThreadworkDocument,
    node: Node,
    typeName: String,
): CompilerTypeInformation? {
    val normalized = typeName.trim()
    if (normalized.isBlank()) return null
    return defaultCodeIntelligence(document, node).types.firstOrNull { it.name == normalized }
        ?: document.nodes.values.firstOrNull { candidate ->
            candidate.kind == NodeKind.Type && candidate.name == normalized
        }?.let { typeNode ->
            CompilerTypeInformation(
                name = typeNode.name,
                languageId = document.effectiveLanguageId(node.id),
                declaration = typeNode.text.declaration.ifBlank {
                    typeNode.typeDefinition?.fields.orEmpty().joinToString("\n") { field ->
                        "${field.name}: ${document.typeDisplayName(field.typeId)}"
                    }
                },
                documentation = "Shared Threadwork type '${typeNode.name}'.",
                fields = typeNode.typeDefinition?.fields.orEmpty().map { field ->
                    CompilerTypeFieldInfo(field.name, document.typeDisplayName(field.typeId), field.isReference)
                },
            )
        }
        ?: BuiltInTypeIds.all.firstOrNull { it == normalized }?.let { builtin ->
            CompilerTypeInformation(
                name = builtin,
                languageId = document.effectiveLanguageId(node.id),
                declaration = builtInTypeDescription(builtin, document.effectiveLanguageId(node.id)),
                documentation = "Built-in Threadwork primitive type.",
            )
        }
}

private fun bufferMethodsFor(
    languageId: String,
    bufferName: String,
    input: Boolean,
): List<CompilerCodeMember> = when (languageId.lowercase()) {
    "javascript", "typescript" -> listOf(
        CompilerCodeMember("$bufferName.push(item)", "append item", "Append an item to this FIFO buffer."),
        CompilerCodeMember("$bufferName.shift()", "take next item", "Remove and return the next item from this FIFO buffer."),
        CompilerCodeMember("$bufferName.length", "buffer length"),
    )

    "kotlin" -> listOf(
        CompilerCodeMember("$bufferName.addLast(item)", "append item", "Append an item to this ArrayDeque buffer."),
        CompilerCodeMember("$bufferName.removeFirst()", "take next item", "Remove and return the first item."),
        CompilerCodeMember("$bufferName.isEmpty()", "empty check"),
        CompilerCodeMember("$bufferName.size", "buffer size"),
    )

    "php" -> listOf(
        CompilerCodeMember("$bufferName[] = \$item", "append item", "Append an item to this array buffer."),
        CompilerCodeMember("array_shift($bufferName)", "take next item", "Remove and return the first item."),
        CompilerCodeMember("count($bufferName)", "buffer size"),
    )

    "c" -> buildList {
        if (input) {
            add(CompilerCodeMember("pop($bufferName, &item)", "take next item", "Copy and remove the next item."))
        } else {
            add(CompilerCodeMember("push($bufferName, &item)", "append item", "Copy an item into this output buffer."))
        }
        add(CompilerCodeMember("threadwork_buffer_count($bufferName)", "buffer size"))
    }

    else -> listOf(
        CompilerCodeMember("$bufferName.push(item)", "append item"),
        CompilerCodeMember("$bufferName.pop()", "take next item"),
    )
}

private fun builtInTypeDescription(typeName: String, languageId: String): String = when (typeName) {
    BuiltInTypeIds.String -> when (languageId.lowercase()) {
        "kotlin" -> "String"
        "c" -> "char *"
        else -> "string"
    }

    BuiltInTypeIds.Number -> when (languageId.lowercase()) {
        "kotlin" -> "Double"
        "c" -> "double"
        else -> "number"
    }

    BuiltInTypeIds.Date -> when (languageId.lowercase()) {
        "kotlin" -> "java.time.Instant"
        "c" -> "int64_t"
        else -> "date"
    }

    BuiltInTypeIds.Array -> when (languageId.lowercase()) {
        "kotlin" -> "List<Any?>"
        "c" -> "ThreadworkArray"
        else -> "array"
    }

    else -> typeName
}
