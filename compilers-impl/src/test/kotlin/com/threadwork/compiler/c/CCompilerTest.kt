package com.threadwork.compiler.c

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.BuiltInTypeIds
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CCompilerTest {
    @Test
    fun `C compiler accepts model runtime and transport template overrides`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "in", PortDirection.Input))
        repository.createLink(root, "packet", source.id, "out", target.id, "in")
        val runtime = repository.createNode(root, "@RuntimeSupport", NodeKind.Processor)
        repository.updateNodeText(
            runtime.id,
            runtime.text.copy(declaration = "unsigned long long threadwork_transit = 0ULL;"),
        )
        val transport = repository.createNode(root, "@LinkInstantiation", NodeKind.Processor)
        repository.updateNodeText(
            transport.id,
            transport.text.copy(
                declaration = """
                    {% if not link.isCapability %}
                        threadwork_transit++;
                    {% endif %}
                """.trimIndent(),
            ),
        )

        val result = CCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val output = assertNotNull(result.generatedProject).files.single().content
        assertTrue(output.contains("unsigned long long threadwork_transit = 0ULL;"))
        assertTrue(output.contains("threadwork_transit++;"))
        assertFalse(output.contains("threadwork_buffer_transport(&packet_a, &packet_b)"))
    }

    @Test
    fun `built in boolean fields use C bool`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val packet = repository.createNode(root, "Packet", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            packet.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("isok", BuiltInTypeIds.Boolean))),
        )

        val result = CCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        assertTrue(source.contains("#include <stdbool.h>"))
        assertTrue(source.contains("bool isok;"))
        assertFalse(source.contains("struct boolean"))
    }

    @Test
    fun `shared type entities declare typed link buffers`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val type = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(mutableListOf(TypeFieldDefinition("id", "number"))),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))

        val result = CCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val sourceCode = assertNotNull(result.generatedProject).files.single().content
        assertTrue(sourceCode.contains("typedef struct WorkOrder"))
        assertTrue(sourceCode.contains(".element_size = sizeof(WorkOrder)"))
        assertTrue(sourceCode.contains("transport_orders1(&orders1_a_port, &orders1_b_port)"))
    }

    @Test
    fun `single file compiler orders types prototypes definitions and entry point`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val nested = repository.createNode(root, "nested pipeline", NodeKind.Group)
        val producer = repository.createNode(nested.id, "123 producer", NodeKind.Processor)
        val consumer = repository.createNode(nested.id, "switch", NodeKind.Processor)
        repository.addPort(producer.id, NodePort("records-out", "records", PortDirection.Output))
        repository.addPort(consumer.id, NodePort("records-in", "records", PortDirection.Input))
        repository.updateNodeText(
            producer.id,
            producer.text.copy(
                declaration = """
                    WorkOrder order = { 42 };
                    if (push(orders_to_validator, &order) != THREADWORK_OK) {
                        return THREADWORK_ERROR;
                    }
                """.trimIndent(),
            ),
        )
        val link = repository.createLink(nested.id, "orders to validator", producer.id, "records", consumer.id, "records")
        link.link!!.typeName = "WorkOrder"
        link.link!!.payloadDefinition = """
            typedef struct WorkOrder {
                int id;
            } WorkOrder;
        """.trimIndent()

        val result = CCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Native C"))

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val file = assertNotNull(result.generatedProject).files.single()
        assertTrue(file.path.endsWith(".c"))
        val source = file.content
        assertEquals(1, source.lines().count { it.trim() == "typedef struct WorkOrder {" })
        assertEquals(1, Regex("\\bint main\\(void\\)").findAll(source).count())
        assertEquals(1, source.lines().count { it.trim() == "typedef struct threadwork_context {" })
        assertTrue(source.indexOf("typedef struct WorkOrder") < source.indexOf("static int tw_init_"))
        val producerPrototype = Regex("static int (tw_init_[A-Za-z0-9_]+)\\(threadwork_context \\*context[^;]*\\);").find(source)
        assertNotNull(producerPrototype)
        val producerDefinition = source.indexOf(
            "static int ${producerPrototype.groupValues[1]}(threadwork_context *context, threadwork_buffer *orders_to_validator)\n{",
        )
        assertTrue(producerDefinition > producerPrototype.range.first)
        assertTrue(source.contains("static threadwork_buffer orders_to_validator1_a_port"))
        assertTrue(source.contains("static threadwork_buffer orders_to_validator1_b_port"))
        assertTrue(source.contains("static int transport_orders_to_validator1("))
        assertTrue(source.contains("transport_orders_to_validator1(&orders_to_validator1_a_port, &orders_to_validator1_b_port)"))
        assertFalse(source.contains("static int tw_run_switch("))
    }

    @Test
    fun `identical wire types are emitted once and conflicting definitions fail`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val firstTarget = repository.createNode(root, "target one", NodeKind.Processor)
        val secondTarget = repository.createNode(root, "target two", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "out", PortDirection.Output))
        repository.addPort(firstTarget.id, NodePort("in", "in", PortDirection.Input))
        repository.addPort(secondTarget.id, NodePort("in", "in", PortDirection.Input))
        val definition = "typedef struct Packet { int value; } Packet;"
        val first = repository.createLink(root, "first", source.id, "out", firstTarget.id, "in")
        first.link!!.typeName = "Packet"
        first.link!!.payloadDefinition = definition
        val second = repository.createLink(root, "second", source.id, "out", secondTarget.id, "in")
        second.link!!.typeName = "Packet"
        second.link!!.payloadDefinition = "  typedef struct Packet {  int value;  } Packet;  "

        val valid = CCompiler().compile(repository.getDocument())

        assertTrue(valid.success, valid.diagnostics.joinToString { it.message })
        assertEquals(1, assertNotNull(valid.generatedProject).files.single().content.lines().count { it.contains("typedef struct Packet") })

        second.link!!.payloadDefinition = "typedef struct Packet { double value; } Packet;"
        val invalid = CCompiler().compile(repository.getDocument())

        assertFalse(invalid.success)
        assertTrue(invalid.diagnostics.any { it.message.contains("conflicting payload definitions") })
    }

    @Test
    fun `file based C layout is rejected`() {
        val repository = cProject()
        repository.requireNode(repository.getDocument().rootNodeId).fileLayoutStrategyId =
            DirectFileSystemHomorphismLayoutStrategy.id

        val result = CCompiler().compile(repository.getDocument())

        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.message.contains("only the single-file layout") })
    }

    @Test
    fun `generated translation unit passes a strict native C17 compiler`() {
        if (!hasNativeCompiler()) return
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val workOrder = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            workOrder.id,
            TypeDefinition(
                mutableListOf(
                    TypeFieldDefinition("id", BuiltInTypeIds.Number),
                    TypeFieldDefinition("is_ready", BuiltInTypeIds.Boolean),
                ),
            ),
        )
        val producer = repository.createNode(root, "producer", NodeKind.Processor)
        val consumer = repository.createNode(root, "consumer", NodeKind.Processor)
        repository.addPort(producer.id, NodePort("out", "records", PortDirection.Output))
        repository.addPort(producer.id, NodePort("src", "source", PortDirection.Output))
        repository.addPort(consumer.id, NodePort("in", "records", PortDirection.Input))
        repository.addPort(consumer.id, NodePort("builder", "builder", PortDirection.Input))
        repository.updateNodeText(
            producer.id,
            producer.text.copy(
                declaration = """
                    WorkOrder order = { 42, true };
                    if (push(records, &order) != THREADWORK_OK) {
                        return THREADWORK_ERROR;
                    }
                """.trimIndent(),
            ),
        )
        val link = repository.createLink(root, "records", producer.id, "records", consumer.id, "records")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = workOrder.id.value))
        val sourceCapability = repository.createLink(root, "producer_source", producer.id, "source", consumer.id, "builder")
        repository.updateLinkData(
            sourceCapability.id,
            requireNotNull(sourceCapability.link).copy(interactionKind = LinkInteractionKinds.Source),
        )
        val result = CCompiler().compile(repository.getDocument())
        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        assertTrue(source.contains("#include <stdbool.h>"))
        assertTrue(source.contains("bool is_ready;"))
        assertFalse(source.contains("struct boolean"))
        assertTrue(source.contains("int push(void *target, void *package)"))
        assertTrue(source.contains("int pop(void *target, void *package)"))
        assertTrue(source.contains("size_t threadwork_buffer_count(const threadwork_buffer *buffer)"))
        val directory = createTempDirectory("threadwork-c-compiler-")
        try {
            val sourceFile = directory.resolve("generated.c")
            val executable = directory.resolve("generated")
            Files.writeString(sourceFile, source)
            val process = ProcessBuilder(
                "cc",
                "-std=c17",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-pedantic",
                sourceFile.toString(),
                "-o",
                executable.toString(),
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), "$output\n\n$source")
            assertEquals(0, ProcessBuilder(executable.toString()).start().waitFor())
            assertTrue(source.contains("getSource"))
            assertFalse(source.contains("producer_source1_a_port"))
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun `service library declarations allow system includes at translation unit scope`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val library = repository.createNode(root, "lib_math", NodeKind.Processor)
        repository.updateNodeText(
            library.id,
            library.text.copy(
                declaration = """
                    #include <limits.h>

                    typedef struct lib_math {
                        int sentinel;
                    } lib_math;

                    int lib_math_maximum(void) {
                        return INT_MAX;
                    }
                """.trimIndent(),
            ),
        )

        val result = CCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        assertEquals(1, source.lines().count { it.trim() == "#include <limits.h>" })
        assertTrue(source.indexOf("#include <limits.h>") < source.indexOf("int main(void)"))
        assertTrue(source.contains("int lib_math_maximum(void)"))
    }

    @Test
    fun `C library links expose prefixed function pointers instead of service instances`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val library = repository.createNode(root, "lib_math", NodeKind.Processor)
        val processor = repository.createNode(root, "calculate", NodeKind.Processor)
        val sink = repository.createNode(root, "result_sink", NodeKind.Processor)
        repository.addPort(library.id, NodePort("service", "service", PortDirection.Output))
        repository.addPort(processor.id, NodePort("math", "math", PortDirection.Input))
        repository.addPort(processor.id, NodePort("result", "result", PortDirection.Output))
        repository.addPort(sink.id, NodePort("result", "result", PortDirection.Input))
        repository.updateNodeText(
            library.id,
            library.text.copy(
                declaration = """
                    int maximum(int left, int right) {
                        return left > right ? left : right;
                    }
                """.trimIndent(),
            ),
        )
        repository.updateNodeText(
            processor.id,
            processor.text.copy(declaration = "int result_value = math_service__maximum(2, 4);\n(void)result_value;"),
        )
        val serviceLink = repository.createLink(root, "math_service", library.id, "service", processor.id, "math")
        repository.updateLinkData(
            serviceLink.id,
            requireNotNull(serviceLink.link).copy(interactionKind = LinkInteractionKinds.Library),
        )
        repository.createLink(root, "result", processor.id, "result", sink.id, "result")

        val compiler = CCompiler()
        val result = compiler.compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = assertNotNull(result.generatedProject).files.single().content
        assertTrue(source.contains("int (*math_service__maximum)(int left, int right) = maximum;"), source)
        assertFalse(source.contains("lib_math math_service"))
        val intelligence = compiler.codeIntelligence(repository.getDocument(), processor)
        assertTrue(
            intelligence.symbols.any {
                it.name == "math_service__maximum" && it.kind == CompilerCodeSymbolKind.LibraryFunction
            },
        )
        assertFalse(intelligence.symbols.any { it.name == "math_service" })
        val header = compiler.generatedFunctionHeader(
            repository.getDocument(),
            processor,
            NodeTextSection.Declaration,
        )
        assertTrue(header.contains("threadwork_buffer *result"))
        assertFalse(header.contains("math_service"))
        compileStrictlyWhenAvailable(source)
    }

    @Test
    fun `processing node declarations reject include directives`() {
        val repository = cProject()
        val root = repository.getDocument().rootNodeId
        val processor = repository.createNode(root, "worker", NodeKind.Processor)
        repository.updateNodeText(processor.id, processor.text.copy(declaration = "#include <limits.h>"))

        val result = CCompiler().compile(repository.getDocument())

        assertFalse(result.success)
        assertTrue(result.diagnostics.any { it.message.contains("only valid in service-library declarations") })
    }

    private fun cProject(): InMemoryDocumentRepository {
        val repository = InMemoryDocumentRepository(newDocument("C Project"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(
            root,
            TechnologyMetadata(
                languageId = "c",
                technologyId = "c-native",
                compilerId = "c-compiler",
                fileExtension = "c",
                contentType = "text/x-c",
            ),
        )
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        return repository
    }

    private fun hasNativeCompiler(): Boolean =
        runCatching {
            ProcessBuilder("cc", "--version").start().waitFor() == 0
        }.getOrDefault(false)

    private fun compileStrictlyWhenAvailable(source: String) {
        if (!hasNativeCompiler()) return
        val directory = createTempDirectory("threadwork-c-library-")
        try {
            val sourceFile = directory.resolve("generated.c")
            val executable = directory.resolve("generated")
            Files.writeString(sourceFile, source)
            val process = ProcessBuilder(
                "cc",
                "-std=c17",
                "-Wall",
                "-Wextra",
                "-Werror",
                "-pedantic",
                sourceFile.toString(),
                "-o",
                executable.toString(),
            ).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            assertEquals(0, process.waitFor(), "$output\n\n$source")
        } finally {
            directory.toFile().deleteRecursively()
        }
    }
}
