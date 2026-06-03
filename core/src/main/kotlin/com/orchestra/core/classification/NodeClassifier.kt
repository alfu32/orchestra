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
    StaticFile,
    CompilerTemplate,
}

enum class LinkStereotype {
    Transport,
    ErrorPipe,
    UsageImport,
    DependencyInjection,
}

object NodeClassifier {
    fun classify(node: Node): NodeStereotype {
        return classify(node, node.incomingLinks.size, node.outgoingLinks.size)
    }

    fun classify(document: InflowDocument, node: Node): NodeStereotype {
        return classify(node, dataIncomingLinks(document, node).size, node.outgoingLinks.size)
    }

    fun dataIncomingLinks(document: InflowDocument, node: Node): List<Node> {
        return node.incomingLinks
            .mapNotNull(document.nodes::get)
            .filter { linkNode -> countsAsDataInput(document, node, linkNode) }
    }

    private fun classify(node: Node, incomingCount: Int, outgoingCount: Int): NodeStereotype {
        val name = node.name.trim()
        return when {
            node.kind == NodeKind.Link || node.link != null -> NodeStereotype.Link
            node.kind == NodeKind.Node -> NodeStereotype.Node
            name.isStaticFileTemplateName() -> NodeStereotype.StaticFile
            name.isCompilerTemplateName() -> NodeStereotype.CompilerTemplate
            node.children.isNotEmpty() && name.startsOrEndsWith("error") -> NodeStereotype.CompositeErrorHandler
            node.children.isNotEmpty() && name.startsOrEndsWith("test") -> NodeStereotype.TestSuite
            node.children.isNotEmpty() -> NodeStereotype.CompositeWorker
            name.startsOrEndsWithAnyOf("service", "client", "library", "lib") -> NodeStereotype.ServiceLibrary
            name.startsOrEndsWith("error") -> NodeStereotype.ErrorHandler
            name.startsOrEndsWith("test") -> NodeStereotype.Test
            incomingCount > 0 && outgoingCount > 0 -> NodeStereotype.Transformer
            incomingCount == 0 && outgoingCount > 0 -> NodeStereotype.Generator
            incomingCount > 0 && outgoingCount == 0 -> NodeStereotype.Sink
            node.kind == NodeKind.Processor -> NodeStereotype.ProcessingUnit
            else -> NodeStereotype.Script
        }
    }

    private fun countsAsDataInput(document: InflowDocument, node: Node, linkNode: Node): Boolean {
        val link = linkNode.link ?: return false
        if (link.targetNodeId != node.id) return false
        val source = document.nodes[link.sourceNodeId] ?: return true
        val sourceIsLibrary = classify(source) == NodeStereotype.ServiceLibrary
        if (!sourceIsLibrary) return true
        return LinkClassifier.classify(document, linkNode) !in dependencyLikeLinks
    }

    private val dependencyLikeLinks = setOf(
        LinkStereotype.UsageImport,
        LinkStereotype.DependencyInjection,
    )
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
fun Node.stereotype(document: InflowDocument): NodeStereotype = NodeClassifier.classify(document, this)

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

fun String.isCompilerTemplateName(): Boolean {
    val normalized = trim().lowercase()
    return normalized == "@compiler" ||
        NodeStereotype.entries.any { normalized == "@${it.name}".lowercase() }
}

private fun String.isStaticFileTemplateName(): Boolean =
    trim().equals("@StaticFile", ignoreCase = true)
