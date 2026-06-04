package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.ClassifiedFilesystemLayoutStrategy
import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.api.DirectFileSystemHomorphismLayoutStrategy
import com.orchestra.compiler.api.LayoutStrategy
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.classification.NodeStereotype
import com.orchestra.core.classification.stereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.getElementsByIds

class JSCompiler : GenericCompiler() {
    override val id: String = "nodejs-compiler"
    override val displayName: String = "NodeJSCompilerGenerated"
    override val supportedLanguageIds: Set<String> = setOf("javascript")
    override val supportedTechnologyIds: Set<String> = setOf("nodejs")
    override val providedTechnologies: List<CompilerTechnology> = listOf(
        CompilerTechnology(supportedLanguageIds.first(), supportedTechnologyIds.first()),
    )

    override fun layoutStrategy(options: CompilerOptions): LayoutStrategy = DirectFileSystemHomorphismLayoutStrategy

    override fun supports(document: InflowDocument): Boolean = true
    override fun validate(document: InflowDocument) = emptyList<com.orchestra.core.diagnostics.Diagnostic>()
    override fun compile(document: InflowDocument, options: CompilerOptions) =
        com.orchestra.compiler.generic.compileWithMethodDispatch(this, document, options)

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        listOf("package.json", "config.json", "tsconfig.json", "vite.js")

    override fun getNodeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return if (node.children.isNotEmpty()) compositeDeclaration(document, node, options) else node.text.declaration
    }

    override fun getNodeInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return if (node.children.isNotEmpty()) "${node.name}()" else "${node.name}()"
    }

    override fun getProcessorDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return if (node.children.isNotEmpty()) compositeDeclaration(document, node, options) else node.text.declaration
    }
    override fun getProcessorInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return if (node.children.isNotEmpty()) "${node.name}()" else "${node.name}()"
    }

    override fun getLinkDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return node.text.declaration
    }

    override fun getLinkInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String {
        return "${node.name}()"
    }

    override fun getGroupDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String = compositeDeclaration(document, node, options)

    override fun getGroupInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String  {
        return "${node.name}()"
    }

    override fun getNoteDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String  {
        return node.text.declaration
    }

    override fun getNoteInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String  {
        return node.text.instantiation
    }

    private fun processingUnitText(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val name = node.name
        val incomingLinks = document.getElementsByIds(node.incomingLinks)
        val inputPorts = incomingLinks.filter { (_, nd) ->
            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
            LinkClassifier.classify(document, srcNode) != LinkStereotype.DependencyInjection
        }
        val libraries = incomingLinks.filter { (_, nd) ->
            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
            LinkClassifier.classify(document, srcNode) == LinkStereotype.DependencyInjection
        }
        val outgoingLinks = document.getElementsByIds(node.outgoingLinks)
        val portArgs = (inputPorts + outgoingLinks).map { port -> port.value.name }.joinToString(",")
        return """
            function create_${name}(){
                ${libraries.values.flatMap { l -> listOf(l.text.instantiation, l.text.declaration) }.joinToString("\n")}
                ${node.text.instantiation.trimIndent()}
                function ${name}(${portArgs}){
                       ${node.text.declaration.trimIndent()}
                }
            }
        """.trimIndent()
    }

    private fun compositeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val children = document.getElementsByIds(node.children)

        val dependencies = document
            .getElementsByIds(node.incomingLinks)
            .map { link -> document.getElementById(link.value.link!!.sourceNodeId) }
            .filter { source -> source!!.stereotype() == NodeStereotype.ServiceLibrary }
        val incoming = document
            .getElementsByIds(node.outgoingLinks)
            .filter { link -> document.getElementById(link.value.link!!.sourceNodeId)!!.stereotype() != NodeStereotype.ServiceLibrary }
            .map { link -> document.getElementById(link.value.id) }
        val outgoing = document
            .getElementsByIds(node.outgoingLinks)
            .map { link -> document.getElementById(link.value.id) }

        val portDeclarations = (incoming + outgoing).joinToString("\n") {
            it!!.text.declaration
        }
        val portInstantiations = (incoming + outgoing).joinToString("\n") {"""const ${it!!.name} = ${it.text.instantiation}"""}
        val librariesDeclarations = dependencies.joinToString("\n"){ it!!.text.declaration }
        val librariesInstantiations = dependencies.joinToString("\n"){"""const ${it!!.name} = ${it.text.instantiation}"""}
        val childProcessingNodesDeclarations = children.values.filter{it.kind == NodeKind.Processor}.joinToString("\n") { it.text.declaration }
        val childLinksTransportInvocations = (incoming + outgoing).joinToString("\n") {"""const ${it!!.name} = ${it.text.instantiation}"""}
        val childProcessingNodesInvocations = children.values.filter{it.kind == NodeKind.Processor}.joinToString("\n") { "${it.name}()" }
        return """
            function ${node.name}(){
                //node.text.declaration
                ${node.text.declaration}
                //portDeclarations
                $portDeclarations
                //portInstantiations
                $portInstantiations
                //librariesDeclarations
                $librariesDeclarations
                //librariesInstantiations
                $librariesInstantiations
                //childProcessingNodesDeclarations
                $childProcessingNodesDeclarations
                
                function run(){
                    //linksTransportInvocations
                    $childLinksTransportInvocations
                    //childrenInvocations
                    $childProcessingNodesInvocations
                }
                return run()
            }
            """.trimIndent()
    }
}
