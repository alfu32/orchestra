package com.threadwork.compiler.php

import com.threadwork.compiler.api.CompilerOptions
import com.threadwork.compiler.api.CompilerCodeSymbolKind
import com.threadwork.compiler.api.SingleFileLayoutStrategy
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodePort
import com.threadwork.core.model.PortDirection
import com.threadwork.core.model.LinkInteractionKinds
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.core.model.TypeDefinition
import com.threadwork.core.model.TypeFieldDefinition
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertTrue

class PhpCompilerTest {
    @Test
    fun `declared types are generated for typed links`() {
        val repository = InMemoryDocumentRepository(newDocument("Typed PHP"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        val type = repository.createNode(root, "WorkOrder", NodeKind.Type)
        repository.updateNodeTypeDefinition(
            type.id,
            TypeDefinition(
                mutableListOf(
                    TypeFieldDefinition("id", "number"),
                    TypeFieldDefinition("enabled", "boolean"),
                ),
            ),
        )
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val target = repository.createNode(root, "target", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "orders", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "orders", PortDirection.Input))
        val link = repository.createLink(root, "orders", source.id, "orders", target.id, "orders")
        repository.updateLinkData(link.id, requireNotNull(link.link).copy(typeDefinitionId = type.id.value))

        val result = PhpCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = requireNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("final class WorkOrder"))
        assertTrue(generated.contains("public float \$id"))
        assertTrue(generated.contains("public bool \$enabled"))
        assertTrue(generated.contains("transport_orders1(\$orders1_a_port, \$orders1_b_port)"))
    }

    @Test
    fun `service libraries emit prefixed aliases without injected variables`() {
        val repository = InMemoryDocumentRepository(newDocument("Dependency PHP"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        val library = repository.createNode(root, "lib_clock", NodeKind.Processor)
        val worker = repository.createNode(root, "worker", NodeKind.Processor)
        repository.addPort(library.id, NodePort("service", "service", PortDirection.Output))
        repository.addPort(worker.id, NodePort("service", "service", PortDirection.Input))
        val dependency = repository.createLink(root, "clock_service", library.id, "service", worker.id, "service")
        repository.updateLinkData(
            dependency.id,
            requireNotNull(dependency.link).copy(interactionKind = LinkInteractionKinds.Library),
        )
        repository.updateNodeText(
            library.id,
            library.text.copy(
                declaration = """
                    function now_text(string ${'$'}prefix = ''): string
                    {
                        return ${'$'}prefix . 'now';
                    }
                """.trimIndent(),
            ),
        )
        repository.updateNodeText(
            worker.id,
            worker.text.copy(declaration = "\$value = clock_service__now_text('threadwork-');"),
        )

        val compiler = PhpCompiler()
        val result = compiler.compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val generated = requireNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(generated.contains("function now_text(string \$prefix = ''): string"))
        assertTrue(generated.contains("function clock_service__now_text(string \$prefix = ''): string"))
        assertTrue(generated.contains("return now_text(\$prefix);"))
        assertTrue(!generated.contains("\$clock_service1 ="))
        assertTrue(!generated.contains("mixed \$clock_service"))
        val intelligence = compiler.codeIntelligence(repository.getDocument(), worker)
        assertTrue(intelligence.symbols.any {
            it.name == "clock_service__now_text" && it.kind == CompilerCodeSymbolKind.LibraryFunction
        })
        assertTrue(intelligence.symbols.none { it.name == "\$clock_service" })
    }

    @Test
    fun `single file layout inlines child declarations without require statements`() {
        val repository = InMemoryDocumentRepository(newDocument("Single PHP"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "read_data", NodeKind.Processor)
        repository.createNode(root, "write_data", NodeKind.Processor)

        val result = PhpCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Single PHP"))

        assertTrue(result.success)
        val source = requireNotNull(result.generatedProject).files
            .filter { it.path.endsWith(".php") }
            .joinToString("\n") { it.content }
        assertTrue(source.contains("function read_data"))
        assertTrue(source.contains("function write_data"))
        assertTrue(source.contains("function Single_PHP"))
        assertTrue(!source.contains("require_once"))
        assertTrue(source.windowed("<?php".length).count { it == "<?php" } == 1)
    }

    @Test
    fun `PHP compiler applies composite single-file template overrides`() {
        val repository = InMemoryDocumentRepository(newDocument("PHP loop override"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        repository.requireNode(root).fileLayoutStrategyId = SingleFileLayoutStrategy.id
        repository.createNode(root, "worker", NodeKind.Processor)
        val override = repository.createNode(root, "@CompositeSingleFile", NodeKind.Processor)
        repository.updateNodeText(
            override.id,
            override.text.copy(
                declaration = """
                    <?php
                    /* custom PHP continuous runner */
                    {{ runtimeSupport }}
                    {{ inlineChildDeclarationsWithoutPhpTag }}
                    {{ ownDeclaration }}
                """.trimIndent(),
            ),
        )

        val result = PhpCompiler().compile(repository.getDocument())

        assertTrue(result.success, result.diagnostics.joinToString { it.message })
        val source = requireNotNull(result.generatedProject).files.joinToString("\n") { it.content }
        assertTrue(source.contains("custom PHP continuous runner"))
    }

    @Test
    fun `link compilation generates named double buffered transport`() {
        val repository = InMemoryDocumentRepository(newDocument("Transport Project"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        val source = repository.createNode(root, "read_file", NodeKind.Processor)
        val target = repository.createNode(root, "write_file", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "record_out", PortDirection.Output))
        repository.addPort(target.id, NodePort("in", "record_in", PortDirection.Input))
        repository.createLink(root, "input_record", source.id, "record_out", target.id, "record_in")

        val result = PhpCompiler().compile(repository.getDocument(), CompilerOptions(projectName = "Transport Project"))

        assertTrue(result.success)
        val project = requireNotNull(result.generatedProject)
        val generated = project.files.joinToString("\n") { it.content }
        assertTrue(generated.contains("\$input_record1_a_port = [];"))
        assertTrue(generated.contains("\$input_record1_b_port = [];"))
        assertTrue(generated.contains("function transport_input_record1(array &\$a, array &\$b)"))
        assertTrue(generated.contains("init_read_file_") && generated.contains("run_read_file_"))
        assertTrue(generated.contains("init_write_file_") && generated.contains("run_write_file_"))
        assertTrue(generated.contains("transport_input_record1(\$input_record1_a_port, \$input_record1_b_port);"))
        assertTrue(generated.contains("threadwork_record_transit()"))
        assertTrue(generated.contains("threadwork_network_has_recent_transit()"))
    }

    @Test
    fun `PHP code intelligence exposes generated PHP scope and runtime names`() {
        val repository = InMemoryDocumentRepository(newDocument("PHP completion"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "php", technologyId = "php"))
        val source = repository.createNode(root, "source", NodeKind.Processor)
        val worker = repository.createNode(root, "worker", NodeKind.Processor)
        val sink = repository.createNode(root, "sink", NodeKind.Processor)
        val library = repository.createNode(root, "library", NodeKind.Processor)
        repository.addPort(source.id, NodePort("out", "packet", PortDirection.Output))
        repository.addPort(source.id, NodePort("source", "artifact", PortDirection.Output))
        repository.addPort(worker.id, NodePort("in", "packet", PortDirection.Input))
        repository.addPort(worker.id, NodePort("out", "result", PortDirection.Output))
        repository.addPort(worker.id, NodePort("source", "artifact", PortDirection.Input))
        repository.addPort(sink.id, NodePort("in", "result", PortDirection.Input))
        repository.addPort(library.id, NodePort("service", "service", PortDirection.Output))
        repository.addPort(worker.id, NodePort("service", "service", PortDirection.Input))
        repository.createLink(root, "incoming_packet", source.id, "packet", worker.id, "packet")
        repository.createLink(root, "outgoing_result", worker.id, "result", sink.id, "result")
        val sourceCapability = repository.createLink(root, "page_source", source.id, "artifact", worker.id, "artifact")
        repository.updateLinkData(
            sourceCapability.id,
            requireNotNull(sourceCapability.link).copy(interactionKind = LinkInteractionKinds.Source),
        )
        val service = repository.createLink(root, "clock_service", library.id, "service", worker.id, "service")
        repository.updateLinkData(
            service.id,
            requireNotNull(service.link).copy(interactionKind = LinkInteractionKinds.Library),
        )
        repository.updateNodeText(
            library.id,
            library.text.copy(declaration = "function now_text(): string { return 'now'; }"),
        )

        val compiler = PhpCompiler()
        val intelligence = compiler.codeIntelligence(repository.getDocument(), worker)

        assertTrue(intelligence.symbols.any {
            it.name == "\$incoming_packet" && it.kind == CompilerCodeSymbolKind.InputBuffer
        })
        assertTrue(intelligence.symbols.any {
            it.name == "\$outgoing_result" && it.kind == CompilerCodeSymbolKind.OutputBuffer
        })
        assertTrue(intelligence.symbols.any {
            it.name == "clock_service__now_text" && it.kind == CompilerCodeSymbolKind.LibraryFunction
        })
        assertTrue(intelligence.symbols.any {
            it.name == "\$page_source" &&
                it.kind == CompilerCodeSymbolKind.SourceCapability &&
                it.members.any { member -> member.name == "\$page_source->getSource(parameters)" }
        })
        assertTrue(intelligence.symbols.any {
            it.name == "\$context" && it.kind == CompilerCodeSymbolKind.RuntimeSymbol
        })
        assertTrue(intelligence.symbols.any {
            it.name == "\$GLOBALS['threadwork_running']" && it.kind == CompilerCodeSymbolKind.RuntimeSymbol
        })
        assertTrue(intelligence.symbols.any {
            it.name == "threadwork_network_has_recent_transit" && it.kind == CompilerCodeSymbolKind.RuntimeSymbol
        })
        assertTrue(
            compiler.generatedFunctionHeader(
                repository.getDocument(),
                worker,
                com.threadwork.core.model.NodeTextSection.Declaration,
            ).contains("array &\$incoming_packet") &&
                compiler.generatedFunctionHeader(
                    repository.getDocument(),
                    worker,
                    com.threadwork.core.model.NodeTextSection.Declaration,
                ).contains("mixed \$clock_service").not(),
        )
    }
}
