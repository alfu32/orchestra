package com.threadwork.completion

import com.threadwork.core.model.NodeId
import com.threadwork.core.model.NodeKind
import com.threadwork.core.model.NodeTextSection
import com.threadwork.core.model.TechnologyMetadata
import com.threadwork.storage.InMemoryDocumentRepository
import com.threadwork.storage.newDocument
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeclarationSymbolsTest {
    @Test
    fun `extracts C function prototypes and named types`() {
        val extractor = BuiltInDeclarationSymbolExtractors.all.single { it.supports("c") }
        val symbols = extractor.extract(
            source = """
                typedef struct Packet Packet;
                struct RuntimeContext {
                    int active;
                };

                static int transform_packet(const Packet *input, Packet *output) {
                    return 0;
                }

                void flush_packets(RuntimeContext *context);
                // void ignored_comment(void);
            """.trimIndent(),
            languageId = "c",
            ownerNodeId = NodeId("processor"),
            ownerNodeName = "processor",
        )

        assertEquals(
            setOf("Packet", "RuntimeContext", "transform_packet", "flush_packets"),
            symbols.mapTo(linkedSetOf()) { it.name },
        )
        assertEquals(DeclarationSymbolKind.Struct, symbols.single { it.name == "Packet" }.kind)
        assertEquals(DeclarationSymbolKind.Function, symbols.single { it.name == "transform_packet" }.kind)
        assertTrue("const Packet *input" in symbols.single { it.name == "transform_packet" }.header)
        assertTrue(symbols.none { it.name == "ignored_comment" })
    }

    @Test
    fun `extracts Kotlin JavaScript and PHP declaration headers`() {
        val cases = listOf(
            Triple("kotlin", "data class Packet(val id: Long)\nfun transform(packet: Packet): Packet = packet", setOf("Packet", "transform")),
            Triple("javascript", "class Packet {}\nconst transform = (packet) => packet;", setOf("Packet", "transform")),
            Triple("php", "final class Packet {}\nfunction transform(Packet ${'$'}packet): Packet { return ${'$'}packet; }", setOf("Packet", "transform")),
        )

        cases.forEach { (language, source, expected) ->
            val extractor = BuiltInDeclarationSymbolExtractors.all.first { it.supports(language) }
            val actual = extractor.extract(source, language, NodeId(language), language).mapTo(linkedSetOf()) { it.name }
            assertEquals(expected, actual, language)
        }
    }

    @Test
    fun `makes declarations from related entities available as completions`() {
        val repository = InMemoryDocumentRepository(newDocument("symbols"))
        val root = repository.getDocument().rootNodeId
        repository.updateNodeTechnology(root, TechnologyMetadata(languageId = "c", technologyId = "native-c"))
        val producer = repository.createNode(root, "producer", NodeKind.Processor)
        val consumer = repository.createNode(root, "consumer", NodeKind.Processor)
        repository.updateNodeText(
            producer.id,
            repository.requireNode(producer.id).text.copy(
                declaration = "Packet produce_packet(RuntimeContext *context) { return context->packet; }",
            ),
        )

        val request = CompletionRequest(
            nodeId = consumer.id,
            textSection = NodeTextSection.Declaration,
            languageId = "c",
            technologyId = "native-c",
            cursorOffset = 3,
            fullText = "pro",
            currentLine = "pro",
            prefix = "pro",
        )
        val service = ModelAwareCompletionService(repository::getDocument)
        val suggestion = service.getSuggestions(request).single { it.label == "produce_packet" }

        assertEquals(CompletionSuggestionKind.UserSymbol, suggestion.kind)
        assertTrue("function from producer" in suggestion.detail)
        assertTrue("Packet produce_packet" in suggestion.documentation)
        assertTrue(service.getDeclarationSymbols(request).any { it.name == "produce_packet" })
    }
}
