@file:Suppress("OVERRIDE_DEPRECATION")

package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.compiler.generic.GenericCompiler
import com.orchestra.core.classification.LinkClassifier
import com.orchestra.core.classification.LinkStereotype
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node
import com.orchestra.core.model.NodeKind
import com.orchestra.core.model.getElementById
import com.orchestra.core.model.getElementsByIds

@Suppress("DEPRECATION")
class JSCompiler : GenericCompiler() {
    override val id: String = "nodejs-compiler"
    override val displayName: String = "NodeJSCompilerGenerated"
    override val supportedLanguageIds: Set<String> = setOf("javascript")
    override val supportedTechnologyIds: Set<String> = setOf("nodejs")
    override val providedTechnologies: List<CompilerTechnology> = listOf(CompilerTechnology(supportedLanguageIds.first(), supportedTechnologyIds.first()))

    override fun supports(document: InflowDocument): Boolean = true
    override fun validate(document: InflowDocument) = emptyList<com.orchestra.core.diagnostics.Diagnostic>()
    override fun compile(document: InflowDocument, options: CompilerOptions) =
        com.orchestra.compiler.generic.compileWithMethodDispatch(this, document, options)

    override fun getStaticFiles(document: InflowDocument, options: CompilerOptions): List<String> = listOf("package.json","config.json","tsconfig.json","vite.js")


    override fun getNode(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getLink(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getProcessingUnit(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val name = node.name
        val incomingLinks = document.getElementsByIds(node.incomingLinks)
        val inputPorts = incomingLinks.filter { (id,nd) ->
            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
            val stereotype = LinkClassifier.classify(document, srcNode)
            stereotype != LinkStereotype.DependencyInjection
        }
        val libraries = incomingLinks.filter { (id,nd) ->
            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
            val stereotype = LinkClassifier.classify(document, srcNode)
            stereotype == LinkStereotype.DependencyInjection
        }
        val outgoingLinks = document.getElementsByIds(node.outgoingLinks)
        val outputPorts = outgoingLinks
        val portArgsDeclarations = (inputPorts+outputPorts).map{ port -> "${port.value.name}:${port.value.link!!.typeName}"}
        val portArgs = (inputPorts+outputPorts).map{ port -> "${port.value.name} "}.joinToString(",")
        val depsArgsDeclarations = (libraries).map{ port -> "${port.value.name}:${port.value.link!!.typeName}: "}
        return """
            function create_${name}(){
                ${libraries.values.flatMap { l -> listOf(l.text.instantiation,l.text.declaration) }.joinToString("\n")}
                ${node.text.instantiation.trimIndent()}
                function ${name}(${portArgs }){
                       ${node.text.declaration.trimIndent()}
                }
            }
        """.trimIndent()
    }


    override fun getCompositeWorker(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val children = document.getElementsByIds(node.children)
        return """
            function ${node.name}(${node.ports.joinToString(",") { p -> p.name }}){
                
                ${
                    children.map { (id,child) ->
                        val name = child.name
                        val incomingLinks = document.getElementsByIds(child.incomingLinks)
                        val inputPorts = incomingLinks.filter { (id,nd) ->
                            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
                            val stereotype = LinkClassifier.classify(document, srcNode)
                            stereotype != LinkStereotype.DependencyInjection
                        }
                        val libraries = incomingLinks.filter { (id,nd) ->
                            val srcNode = document.getElementById(nd.link!!.sourceNodeId)!!
                            val stereotype = LinkClassifier.classify(document, srcNode)
                            stereotype == LinkStereotype.DependencyInjection
                        }
                        val outgoingLinks = document.getElementsByIds(child.outgoingLinks)
                        val portArgsDeclarations = (inputPorts + outgoingLinks).map{ port -> "var ${port.value.name}/*:${port.value.link!!.typeName}*/"}
                        val portArgs = (inputPorts + outgoingLinks).map{ port -> "${port.value.name} "}.joinToString(",")
                        val depsArgsDeclarations = (libraries).map{ port -> "${port.value.name}:${port.value.link!!.typeName}: "}
                        """
                            ${depsArgsDeclarations.joinToString("\n")}}
                            ${portArgsDeclarations.joinToString("\n") }}
                            create_${name}()(${portArgs})
                        """.trimIndent()
                        
                    }
                }
            }
        """.trimIndent()
    }


    override fun getCompositeErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = getCompositeWorker(document,node,options)


    override fun getGenerator(document: InflowDocument, node: Node, options: CompilerOptions): String = getProcessingUnit(document,node,options)


    override fun getTransformer(document: InflowDocument, node: Node, options: CompilerOptions): String =getProcessingUnit(document,node,options)


    override fun getSink(document: InflowDocument, node: Node, options: CompilerOptions): String =getProcessingUnit(document,node,options)


    override fun getScript(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String = getProcessingUnit(document,node,options)

    override fun getServiceLibrary(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
            ${node.text.instantiation}
            ${node.text.declaration}
        """.trimIndent()


    override fun getTransport(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getErrorPipe(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getDependencyInjection(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val sourceLibNode = document.getElementById(node.link!!.sourceNodeId)
        return """
            const ${sourceLibNode!!.name} = require('../libraries/${sourceLibNode.name}.js')
        """.trimIndent()
    }

    override fun getTest(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """ function (){
                ${node.text.instantiation}
                function test_${node.name}() {
                    ${node.text.declaration}
                }}
            }()
        """.trimIndent()


    override fun getTestSuite(document: InflowDocument, node: Node, options: CompilerOptions): String {
        val children = document.getElementsByIds(node.children)
        val tests = children.filter { (_, child) -> child.kind == NodeKind.Processor }
        return """
            ${
                tests.values.joinToString("\n") { ch ->
                    getTest(document,ch,options)
                }
            }
        """.trimIndent()

    }
    override fun getInputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getOutputPort(document: InflowDocument, node: Node, options: CompilerOptions): String = node.text.declaration.trimIndent()


    override fun getStaticFile(document: InflowDocument, node: Node, options: CompilerOptions): String =node.text.declaration.trimIndent()


    override fun getCompilerTemplate(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

        """.trimIndent()
}
