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
    const val InProcessQueue = "in-process.queue"
    const val InProcessMemory = "in-process.memory"
    const val InProcessLocalVariable = "in-process.local-variable"
    const val InProcessMethodArgument = "in-process.method-argument"
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

    const val Default = InProcessQueue

    val catalog: List<LinkTransportKindDescriptor> = listOf(
        LinkTransportKindDescriptor(InProcessQueue, LinkTransportScope.InProcess, "in-process / queue"),
        LinkTransportKindDescriptor(InProcessMemory, LinkTransportScope.InProcess, "in-process / shared memory"),
        LinkTransportKindDescriptor(InProcessLocalVariable, LinkTransportScope.InProcess, "in-process / local variable"),
        LinkTransportKindDescriptor(InProcessMethodArgument, LinkTransportScope.InProcess, "in-process / method argument"),
        LinkTransportKindDescriptor(InterProcessIpc, LinkTransportScope.InterProcess, "inter-process / IPC"),
        LinkTransportKindDescriptor(InterProcessPipe, LinkTransportScope.InterProcess, "inter-process / pipe"),
        LinkTransportKindDescriptor(InterProcessFile, LinkTransportScope.InterProcess, "inter-process / file"),
        LinkTransportKindDescriptor(MachineToMachineRpc, LinkTransportScope.MachineToMachine, "m2m / RPC"),
        LinkTransportKindDescriptor(MachineToMachineHttp, LinkTransportScope.MachineToMachine, "m2m / HTTP"),
        LinkTransportKindDescriptor(MachineToMachineTcp, LinkTransportScope.MachineToMachine, "m2m / TCP"),
        LinkTransportKindDescriptor(MachineToMachineUdp, LinkTransportScope.MachineToMachine, "m2m / UDP"),
        LinkTransportKindDescriptor(MachineToMachineWebSocket, LinkTransportScope.MachineToMachine, "m2m / WebSocket"),
        LinkTransportKindDescriptor(MachineToMachineMessageBus, LinkTransportScope.MachineToMachine, "m2m / message bus"),
        LinkTransportKindDescriptor(MachineToMachineMqtt, LinkTransportScope.MachineToMachine, "m2m / MQTT"),
        LinkTransportKindDescriptor(MachineToMachineGrpc, LinkTransportScope.MachineToMachine, "m2m / gRPC"),
    )

    private val byId: Map<String, LinkTransportKindDescriptor> = catalog.associateBy { it.id }

    fun isKnown(id: String): Boolean = id.trim() in byId

    fun descriptor(id: String): LinkTransportKindDescriptor? = byId[id.trim()]
}
