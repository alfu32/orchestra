package com.orchestra.core.validation

import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.diagnostics.DiagnosticSeverity
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.PortDirection

object DocumentValidator {
    fun validate(document: InflowDocument): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        if (!document.nodes.containsKey(document.rootNodeId)) {
            diagnostics += error("Root node '${document.rootNodeId}' does not exist", document.rootNodeId)
        }

        document.nodes.values.forEach { node ->
            validateParent(document, node, diagnostics)
            validateChildren(document, node, diagnostics)
            validateLinks(document, node, diagnostics)
            validatePorts(node, diagnostics)
        }

        return diagnostics
    }

    private fun validateParent(document: InflowDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
        val parentId = node.parentId ?: return
        val parent = document.nodes[parentId]
        if (parent == null) {
            diagnostics += error("Parent node '$parentId' does not exist", node.id)
            return
        }
        if (node.id !in parent.children) {
            diagnostics += error("Parent '$parentId' does not contain child '${node.id}'", node.id)
        }
    }

    private fun validateChildren(document: InflowDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
        node.children.groupingBy { it }.eachCount().filterValues { it > 1 }.keys.forEach {
            diagnostics += error("Duplicate child id '$it'", node.id)
        }
        node.children.forEach { childId ->
            val child = document.nodes[childId]
            if (child == null) {
                diagnostics += error("Child node '$childId' does not exist", node.id)
            } else if (child.parentId != node.id) {
                diagnostics += error("Child '$childId' does not point back to parent '${node.id}'", childId)
            }
        }
    }

    private fun validateLinks(document: InflowDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
        node.incomingLinks.forEach { if (it !in document.nodes) diagnostics += error("Incoming link '$it' does not exist", node.id) }
        node.outgoingLinks.forEach { if (it !in document.nodes) diagnostics += error("Outgoing link '$it' does not exist", node.id) }

        if (node.kind != NodeKind.Link && node.link == null) return
        val link = node.link
        if (link == null) {
            diagnostics += error("Link node '${node.id}' has no link data", node.id)
            return
        }

        val source = document.nodes[link.sourceNodeId]
        val target = document.nodes[link.targetNodeId]
        if (source == null) diagnostics += error("Link source '${link.sourceNodeId}' does not exist", node.id)
        if (target == null) diagnostics += error("Link target '${link.targetNodeId}' does not exist", node.id)

        source?.ports?.find { it.name == link.sourcePortName && it.direction == PortDirection.Output }
            ?: diagnostics.add(error("Source output port '${link.sourcePortName}' does not exist", node.id))
        target?.ports?.find { it.name == link.targetPortName && it.direction == PortDirection.Input }
            ?: diagnostics.add(error("Target input port '${link.targetPortName}' does not exist", node.id))
    }

    private fun validatePorts(node: Node, diagnostics: MutableList<Diagnostic>) {
        node.ports.groupingBy { it.direction to it.name }.eachCount().filterValues { it > 1 }.keys.forEach { (_, name) ->
            diagnostics += error("Duplicate port name '$name' for same direction", node.id)
        }
    }

    private fun error(message: String, nodeId: NodeId? = null): Diagnostic =
        Diagnostic(DiagnosticSeverity.Error, message, nodeId)
}
