package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
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

    override fun supports(document: InflowDocument): Boolean = true
    override fun validate(document: InflowDocument) = emptyList<com.orchestra.core.diagnostics.Diagnostic>()
    override fun compile(document: InflowDocument, options: CompilerOptions) =
        com.orchestra.compiler.generic.compileWithMethodDispatch(this, document, options)

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> =
        listOf("package.json", "config.json", "tsconfig.json", "vite.js")

    override fun getNodeDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        ""

    override fun getNodeInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNodeDeclaration(document, node, options)

    override fun getProcessorDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (node.stereotype(document)) {
            NodeStereotype.ServiceLibrary -> serviceLibraryText(document, node, options)
            NodeStereotype.Test -> testText(document, node, options)
            else -> processingUnitText(document, node, options)
        }

    override fun getProcessorInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getProcessorDeclaration(document, node, options)

    override fun getLinkDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (LinkClassifier.classify(document, node)) {
            LinkStereotype.DependencyInjection -> dependencyInjectionText(document, node, options)
            else -> node.text.declaration.trimIndent()
        }

    override fun getLinkInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getLinkDeclaration(document, node, options)

    override fun getGroupDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (node.stereotype(document)) {
            NodeStereotype.TestSuite -> testSuiteText(document, node, options)
            else -> compositeText(document, node, options)
        }

    override fun getGroupInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getGroupDeclaration(document, node, options)

    override fun getNoteDeclaration(document: InflowDocument, node: Node, options: CompilerOptions): String =
        when (node.stereotype(document)) {
            NodeStereotype.CompilerTemplate -> compilerTemplateText(node)
            NodeStereotype.Script -> scriptText(node)
            else -> scriptText(node)
        }

    override fun getNoteInstantiation(document: InflowDocument, node: Node, options: CompilerOptions): String =
        getNoteDeclaration(document, node, options)

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

    private fun compositeText(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val children = document.getElementsByIds(node.children)
        return """
            function ${node.name}(${node.ports.joinToString(",") { p -> p.name }}){

                ${
            children.values.joinToString("\n") { child ->
                val name = child.name
                val incomingLinks = document.getElementsByIds(child.incomingLinks)
                val inputPorts = incomingLinks.filter { (_, nd) ->
                    val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
                    LinkClassifier.classify(document, srcNode) != LinkStereotype.DependencyInjection
                }
                val libraries = incomingLinks.filter { (_, nd) ->
                    val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
                    LinkClassifier.classify(document, srcNode) == LinkStereotype.DependencyInjection
                }
                val outgoingLinks = document.getElementsByIds(child.outgoingLinks)
                val portArgsDeclarations = (inputPorts + outgoingLinks).map { port -> "var ${port.value.name}/*:${port.value.link!!.typeName}*/" }
                val portArgs = (inputPorts + outgoingLinks).map { port -> port.value.name }.joinToString(",")
                """
                    ${libraries.values.flatMap { l -> listOf(l.text.instantiation, l.text.declaration) }.joinToString("\n")}
                    ${portArgsDeclarations.joinToString("\n")}
                    create_${name}()(${portArgs})
                """.trimIndent()
            }
        }
            }
        """.trimIndent()
    }

    private fun scriptText(node: Node): String =
        node.text.declaration.trimIndent()

    private fun serviceLibraryText(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
            ${node.text.instantiation}
            ${node.text.declaration}
        """.trimIndent()

    private fun dependencyInjectionText(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val sourceLibNode = document.getElementById(node.link!!.sourceNodeId)
        return """
            const ${sourceLibNode!!.name} = require('../libraries/${sourceLibNode.name}.js')
        """.trimIndent()
    }

    private fun testText(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """function (){
                ${node.text.instantiation}
                function test_${node.name}() {
                    ${node.text.declaration}
                }}
            }()
        """.trimIndent()

    private fun testSuiteText(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val children = document.getElementsByIds(node.children)
        val tests = children.filter { (_, child) -> child.kind == NodeKind.Processor }
        return """
            ${
            tests.values.joinToString("\n") { ch ->
                testText(document, ch, options)
            }
        }
        """.trimIndent()
    }

    private fun compilerTemplateText(node: Node): String =
        node.text.declaration.trimIndent()
}
