package com.threadwork.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class TechnologyMetadataResolutionTest {
    @Test
    fun `project name comes from the root node`() {
        val root = Node(NodeId("root"), "Renamed Project", NodeKind.Group)
        val document = ThreadworkDocument(
            id = "doc",
            name = "Untitled Threadwork",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root),
        )

        assertEquals("Renamed Project", document.projectName())
    }

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
        val document = ThreadworkDocument(
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
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root),
        )

        assertEquals(VOID_LANGUAGE_ID, document.effectiveLanguageId(root.id))
        assertEquals(VOID_TECHNOLOGY_ID, document.effectiveTechnologyId(root.id))
    }

    @Test
    fun `effective technology skips explicit none values and inherits from parent`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            technology = TechnologyMetadata(technologyId = "nodejs"),
        )
        val child = Node(
            id = NodeId("child"),
            name = "child",
            kind = NodeKind.Processor,
            parentId = root.id,
            technology = TechnologyMetadata(technologyId = VOID_TECHNOLOGY_ID),
        )
        root.children += child.id
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, child.id to child),
        )

        assertEquals("nodejs", document.effectiveTechnologyId(child.id))
    }

    @Test
    fun `file export without language or layout does not inherit enclosing workflow settings`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            technology = TechnologyMetadata(languageId = "c", technologyId = "c-native"),
        )
        val exported = Node(
            id = NodeId("exported"),
            name = "dlls",
            kind = NodeKind.Group,
            parentId = root.id,
            technology = TechnologyMetadata(technologyId = "file-export"),
        )
        root.children += exported.id
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, exported.id to exported),
        )

        assertEquals(VOID_LANGUAGE_ID, document.effectiveLanguageId(exported.id))
        assertEquals(VOID_LAYOUT_STRATEGY_ID, document.effectiveLayoutStrategyId(exported.id))
    }

    @Test
    fun `effective layout strategy skips explicit none and blank values and inherits from parent`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            fileLayoutStrategyId = "single-file",
        )
        val child = Node(
            id = NodeId("child"),
            name = "child",
            kind = NodeKind.Processor,
            parentId = root.id,
            fileLayoutStrategyId = VOID_LAYOUT_STRATEGY_ID,
        )
        val grandchild = Node(
            id = NodeId("grandchild"),
            name = "grandchild",
            kind = NodeKind.Processor,
            parentId = child.id,
            fileLayoutStrategyId = "",
        )
        root.children += child.id
        child.children += grandchild.id
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, child.id to child, grandchild.id to grandchild),
        )

        assertEquals("single-file", document.effectiveLayoutStrategyId(child.id))
        assertEquals("single-file", document.effectiveLayoutStrategyId(grandchild.id))
    }

    @Test
    fun `effective responsible and revision use the nearest owning ancestor`() {
        val root = Node(
            id = NodeId("root"),
            name = "root",
            kind = NodeKind.Group,
            responsible = "Ada",
            revision = Revision("R1", "2026-08-17"),
        )
        val child = Node(NodeId("child"), "child", NodeKind.Processor, parentId = root.id)
        val grandchild = Node(
            id = NodeId("grandchild"),
            name = "grandchild",
            kind = NodeKind.Processor,
            parentId = child.id,
            responsible = "Grace",
        )
        root.children += child.id
        child.children += grandchild.id
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root, child.id to child, grandchild.id to grandchild),
        )

        assertEquals("Ada", document.effectiveResponsible(child.id))
        assertEquals("Grace", document.effectiveResponsible(grandchild.id))
        assertEquals(Revision("R1", "2026-08-17"), document.effectiveRevision(grandchild.id))
    }

    @Test
    fun `responsible and revision fall back to none`() {
        val root = Node(NodeId("root"), "root", NodeKind.Group)
        val document = ThreadworkDocument(
            id = "doc",
            name = "doc",
            rootNodeId = root.id,
            nodes = mutableMapOf(root.id to root),
        )

        assertEquals(VOID_RESPONSIBLE, document.effectiveResponsible(root.id))
        assertEquals(null, document.effectiveRevision(root.id))
    }
}
