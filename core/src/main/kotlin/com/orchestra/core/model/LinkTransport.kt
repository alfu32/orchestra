package com.orchestra.core.model

enum class LinkTransportScope {
    InProcess,
    InterProcess,
    MachineToMachine,
    Other,
}

data class LinkTransportKindDescriptor(
    val id: String,
    val scope: LinkTransportScope,
    val label: String,
)

object LinkTransportKinds {
    const val InProcess = "in-process"
    const val InterProcessIpc = "inter-process.ipc"
    const val InterProcessPipe = "inter-process.pipe"
    const val InterProcessFile = "inter-process.file"
    const val MachineToMachineRpc = "m2m.rpc"
    const val MachineToMachineHttp = "m2m.http"
    const val MachineToMachineTcp = "m2m.tcp"
    const val MachineToMachineUdp = "m2m.udp"
    const val MachineToMachineWebSocket = "m2m.websocket"
    const val MachineToMachineMessageBus = "m2m.message-bus"
    const val MachineToMachineMqtt = "m2m.mqtt"
    const val MachineToMachineGrpc = "m2m.grpc"

    const val Default = InProcess

    val catalog: List<LinkTransportKindDescriptor> = listOf(
        LinkTransportKindDescriptor(InProcess, LinkTransportScope.InProcess, "In-process"),
        LinkTransportKindDescriptor(InterProcessIpc, LinkTransportScope.InterProcess, "Inter-process: IPC"),
        LinkTransportKindDescriptor(InterProcessPipe, LinkTransportScope.InterProcess, "Inter-process: pipe"),
        LinkTransportKindDescriptor(InterProcessFile, LinkTransportScope.InterProcess, "Inter-process: file"),
        LinkTransportKindDescriptor(MachineToMachineRpc, LinkTransportScope.MachineToMachine, "M2M: RPC"),
        LinkTransportKindDescriptor(MachineToMachineHttp, LinkTransportScope.MachineToMachine, "M2M: HTTP"),
        LinkTransportKindDescriptor(MachineToMachineTcp, LinkTransportScope.MachineToMachine, "M2M: TCP"),
        LinkTransportKindDescriptor(MachineToMachineUdp, LinkTransportScope.MachineToMachine, "M2M: UDP"),
        LinkTransportKindDescriptor(MachineToMachineWebSocket, LinkTransportScope.MachineToMachine, "M2M: WebSocket"),
        LinkTransportKindDescriptor(MachineToMachineMessageBus, LinkTransportScope.MachineToMachine, "M2M: message bus"),
        LinkTransportKindDescriptor(MachineToMachineMqtt, LinkTransportScope.MachineToMachine, "M2M: MQTT"),
        LinkTransportKindDescriptor(MachineToMachineGrpc, LinkTransportScope.MachineToMachine, "M2M: gRPC"),
    )

    private val byId: Map<String, LinkTransportKindDescriptor> = catalog.associateBy { it.id }
    private val aliases: Map<String, String> = mapOf(
        "in-process.queue" to InProcess,
        "in-process.memory" to InProcess,
        "in-process.local-variable" to InProcess,
        "in-process.method-argument" to InProcess,
    )

    fun canonicalId(id: String): String = aliases[id.trim()] ?: id.trim()

    fun isKnown(id: String): Boolean = canonicalId(id) in byId

    fun descriptor(id: String): LinkTransportKindDescriptor? = byId[canonicalId(id)]
}
