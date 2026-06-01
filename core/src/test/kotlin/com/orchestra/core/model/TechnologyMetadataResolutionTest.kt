package com.orchestra.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TechnologyMetadataResolutionTest {
    @Test
    fun `effective language and technology are inherited from nearest parent`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            technology = TechnologyMetadata(languageId = "kotlin", technologyId = "jvm"),
        )
        val parent = Node(
            id = NodeId("parent"),
            name = "parent",
            kind = NodeKind.Group,
            parentId = root.id,
            technology = TechnologyMetadata(technologyId = "node"),
        )
        val child = Node(
            id = NodeId("child"),
            name = "child",
            kind = NodeKind.Processor,
            parentId = parent.id,
            technology = TechnologyMetadata(languageId = "javascript"),
        )
        root.children += parent.id
        parent.children += child.id
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, parent.id to parent, child.id to child),
        )

        assertEquals("javascript", document.effectiveLanguageId(child.id))
        assertEquals("node", document.effectiveTechnologyId(child.id))
    }

    @Test
    fun `effective language and technology fall back to void values`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val document = InflowDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root),
        )

        assertEquals(VOID_LANGUAGE_ID, document.effectiveLanguageId(root.id))
        assertEquals(VOID_TECHNOLOGY_ID, document.effectiveTechnologyId(root.id))
    }
}
