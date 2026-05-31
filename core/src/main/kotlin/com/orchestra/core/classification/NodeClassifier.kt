package com.orchestra.core.classification

import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.InflowDocument

enum class NodeStereotype {
    Node,
    Link,
    ProcessingUnit,
    CompositeWorker,
    CompositeErrorHandler,
    Generator,
    Transformer,
    Sink,
    Script,
    ErrorHandler,
    ServiceLibrary,
    Transport,
    ErrorPipe,
    DependencyInjection,
    Test,
    TestSuite,
    InputPort,
    OutputPort,
}

enum class LinkStereotype {
    Transport,
    ErrorPipe,
    UsageImport,
    DependencyInjection,
}

object NodeClassifier {
    fun classify(node: Node): NodeStereotype {
        val name = node.name.trim()
        return when {
            node.kind == NodeKind.Link || node.link != null -> NodeStereotype.Link
            node.kind == NodeKind.Node -> NodeStereotype.Node
            node.children.isNotEmpty() && name.startsOrEndsWith("error") -> NodeStereotype.CompositeErrorHandler
            node.children.isNotEmpty() && name.startsOrEndsWith("test") -> NodeStereotype.TestSuite
            node.children.isNotEmpty() -> NodeStereotype.CompositeWorker
            name.startsOrEndsWithAnyOf("service", "client", "library", "lib") -> NodeStereotype.ServiceLibrary
            name.startsOrEndsWith("error") -> NodeStereotype.ErrorHandler
            name.startsOrEndsWith("test") -> NodeStereotype.Test
            node.incomingLinks.isNotEmpty() && node.outgoingLinks.isNotEmpty() -> NodeStereotype.Transformer
            node.incomingLinks.isEmpty() && node.outgoingLinks.isNotEmpty() -> NodeStereotype.Generator
            node.incomingLinks.isNotEmpty() && node.outgoingLinks.isEmpty() -> NodeStereotype.Sink
            node.kind == NodeKind.Processor -> NodeStereotype.ProcessingUnit
            else -> NodeStereotype.Script
        }
    }
}

object LinkClassifier {
    fun classify(document: InflowDocument, linkNode: Node): LinkStereotype {
        val link = linkNode.link ?: return LinkStereotype.Transport
        val transportKind = link.transportKind.trim().lowercase()
        val source = document.nodes[link.sourceNodeId]
        val target = document.nodes[link.targetNodeId]
        val sourceStereotype = source?.stereotype()
        val targetStereotype = target?.stereotype()
        val linkName = linkNode.name.trim()

        return when {
            transportKind in usageKinds ||
                sourceStereotype == NodeStereotype.ServiceLibrary ||
                linkName.containsToken("usage") ||
                linkName.containsToken("use") ||
                linkName.containsToken("import") -> LinkStereotype.UsageImport
            transportKind in errorKinds ||
                targetStereotype == NodeStereotype.ErrorHandler ||
                targetStereotype == NodeStereotype.CompositeErrorHandler ||
                link.sourcePortName.contains("error", ignoreCase = true) ||
                link.targetPortName.contains("error", ignoreCase = true) ||
                linkName.containsToken("error") -> LinkStereotype.ErrorPipe
            transportKind in dependencyKinds ||
                linkName.containsToken("dependency") ||
                linkName.containsToken("inject") -> LinkStereotype.DependencyInjection
            else -> LinkStereotype.Transport
        }
    }

    private val usageKinds = setOf("usage", "use", "import", "library", "lib")
    private val errorKinds = setOf("error", "error-pipe", "exception", "failure")
    private val dependencyKinds = setOf("dependency", "di", "inject", "injection")
}

fun Node.stereotype(): NodeStereotype = NodeClassifier.classify(this)

private fun String.startsOrEndsWith(token: String): Boolean {
    val normalized = lowercase()
    val t = token.lowercase()
    return normalized == t ||
        normalized.startsWith("${t}_") ||
        normalized.startsWith("${t}-") ||
        normalized.startsWith("${t} ") ||
        normalized.endsWith("_$t") ||
        normalized.endsWith("-$t") ||
        normalized.endsWith(" $t")
}

private fun String.startsOrEndsWithAnyOf(vararg tokens: String): Boolean =
    tokens.any { startsOrEndsWith(it) }

private fun String.containsToken(token: String): Boolean {
    val normalized = lowercase()
    val t = token.lowercase()
    return normalized == t ||
        normalized.contains("_$t") ||
        normalized.contains("${t}_") ||
        normalized.contains("-$t") ||
        normalized.contains("${t}-") ||
        normalized.contains(" $t") ||
        normalized.contains("$t ")
}
