package com.threadwork.core.validation

import com.threadwork.core.diagnostics.Diagnostic
import com.threadwork.core.diagnostics.DiagnosticSeverity
import com.threadwork.core.model.ThreadworkDocument
import com.threadwork.core.model.Node
import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.BuiltInTypeIds
import com.threadwork.core.model.closestCommonAncestorId
import com.threadwork.core.model.compositeBoundaryIdsBetween

object DocumentValidator {
    fun validate(document: ThreadworkDocument): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()

        if (!document.nodes.containsKey(document.rootNodeId)) {
            diagnostics += error("Root node '${document.rootNodeId}' does not exist", document.rootNodeId)
        }

        document.nodes.values.forEach { node ->
            validateParent(document, node, diagnostics)
            validateChildren(document, node, diagnostics)
            validateLinks(document, node, diagnostics)
            validatePorts(node, diagnostics)
            validateType(document, node, diagnostics)
        }

        return diagnostics
    }

    private fun validateParent(document: ThreadworkDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
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

    private fun validateChildren(document: ThreadworkDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
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

    private fun validateLinks(document: ThreadworkDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
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
        if (source?.isLink == true) diagnostics += error("Link source '${link.sourceNodeId}' cannot be another link", node.id)
        if (target?.isLink == true) diagnostics += error("Link target '${link.targetNodeId}' cannot be another link", node.id)
        if (source?.isType == true) diagnostics += error("Link source '${link.sourceNodeId}' cannot be a type declaration", node.id)
        if (target?.isType == true) diagnostics += error("Link target '${link.targetNodeId}' cannot be a type declaration", node.id)

        if (source != null && target != null && !source.isLink && !target.isLink && !source.isType && !target.isType) {
            val expectedParentId = document.closestCommonAncestorId(source.id, target.id) ?: document.rootNodeId
            if (node.parentId != expectedParentId) {
                diagnostics += error("Link '${node.id}' must belong to closest common parent '$expectedParentId'", node.id)
            }
            val expectedBoundaries = document.compositeBoundaryIdsBetween(source.id, target.id)
            if (link.compositeBoundaryIds != expectedBoundaries) {
                diagnostics += error("Link '${node.id}' has stale composite boundary traversal", node.id)
            }
        }

        val typeId = link.typeDefinitionId.trim()
        if (typeId.isNotBlank() && !isKnownType(document, typeId)) {
            diagnostics += error("Link '${node.id}' references unknown type '$typeId'", node.id)
        }

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

    private fun validateType(document: ThreadworkDocument, node: Node, diagnostics: MutableList<Diagnostic>) {
        if (node.kind != NodeKind.Type) return
        val definition = node.typeDefinition
        if (definition == null) {
            diagnostics += error("Type node '${node.id}' has no type definition", node.id)
            return
        }
        definition.fields.groupingBy { it.name.trim() }.eachCount()
            .filter { (name, count) -> name.isNotBlank() && count > 1 }
            .keys
            .forEach { diagnostics += error("Duplicate type field '$it'", node.id) }
        definition.fields.forEach { field ->
            if (field.name.isBlank()) diagnostics += error("Type field name cannot be blank", node.id)
            if (!isKnownType(document, field.typeId)) {
                diagnostics += error("Type field '${field.name}' references unknown type '${field.typeId}'", node.id)
            }
        }
    }

    private fun isKnownType(document: ThreadworkDocument, typeId: String): Boolean {
        val normalized = typeId.trim()
        if (normalized in BuiltInTypeIds.all) return true
        return document.nodes[NodeId(normalized)]?.kind == NodeKind.Type
    }

    private fun error(message: String, nodeId: NodeId? = null): Diagnostic =
        Diagnostic(DiagnosticSeverity.Error, message, nodeId)
}
