package com.orchestra.core.diagnostics

import com.orchestra.core.model.NodeId
import com.orchestra.core.model.NodeTextSection

data class Diagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val nodeId: NodeId? = null,
    val textSection: NodeTextSection? = null,
    val line: Int? = null,
    val column: Int? = null,
    val sourcePluginId: String? = null,
)

enum class DiagnosticSeverity {
    Info,
    Warning,
    Error,
}
