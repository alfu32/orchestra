package com.threadwork.core.model

data class LinkInteractionKindDescriptor(
    val id: String,
    val label: String,
    val capability: Boolean,
)

/** Semantic link behavior, independent from the physical transport mechanism. */
object LinkInteractionKinds {
    const val Auto = "auto"
    const val Data = "data"
    const val Library = "lib"
    const val Source = "src"
    const val Runnable = "run"

    val catalog: List<LinkInteractionKindDescriptor> = listOf(
        LinkInteractionKindDescriptor(Auto, "Automatic (legacy)", capability = false),
        LinkInteractionKindDescriptor(Data, "Data transport", capability = false),
        LinkInteractionKindDescriptor(Library, "Library capability (lib)", capability = true),
        LinkInteractionKindDescriptor(Source, "Source capability (src)", capability = true),
        LinkInteractionKindDescriptor(Runnable, "Runnable capability (run)", capability = true),
    )

    private val byId = catalog.associateBy(LinkInteractionKindDescriptor::id)
    private val aliases = mapOf(
        "automatic" to Auto,
        "transport" to Data,
        "library" to Library,
        "source" to Source,
        "runnable" to Runnable,
    )

    fun canonicalId(id: String): String {
        val normalized = id.trim().lowercase()
        return aliases[normalized] ?: normalized
    }

    fun isKnown(id: String): Boolean = canonicalId(id) in byId

    fun isCapability(id: String): Boolean = byId[canonicalId(id)]?.capability == true

    fun descriptor(id: String): LinkInteractionKindDescriptor? = byId[canonicalId(id)]
}
