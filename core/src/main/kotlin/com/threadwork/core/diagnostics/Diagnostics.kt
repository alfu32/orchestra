package com.threadwork.core.diagnostics

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeTextSection
import kotlinx.serialization.Serializable

@Serializable
data class Diagnostic(
    val severity: DiagnosticSeverity,
    val message: String,
    val nodeId: NodeId? = null,
    val textSection: NodeTextSection? = null,
    val line: Int? = null,
    val column: Int? = null,
    val sourcePluginId: String? = null,
)

@Serializable
enum class DiagnosticSeverity {
    Info,
    Warning,
    Error,
}
