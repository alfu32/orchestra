package com.orchestra.compiler.generated.nodejs

import com.orchestra.compiler.api.CompilerOptions
import com.orchestra.compiler.api.CompilerPlugin
import com.orchestra.compiler.api.CompilerTechnology
import com.orchestra.core.diagnostics.Diagnostic
import com.orchestra.core.model.InflowDocument
import com.orchestra.core.model.Node

class JSCompiler : CompilerPlugin {
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


    override fun getLink(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getProcessingUnit(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getCompositeWorker(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getCompositeErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getGenerator(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
// generic override for generator generation
  template: generator init

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
// generic override for generator generation
  template: generator loop

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
""".trimIndent()


    override fun getTransformer(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
// generic override for transformer generation
  template: transformer init

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
// generic override for transformer generation
  template: transformer loop

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
""".trimIndent()


    override fun getSink(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
// generic override for sink generation
  template: sink init

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
// generic override for sink generation
  template: sink loop

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}


${document}
""".trimIndent()


    override fun getScript(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getErrorHandler(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getServiceLibrary(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """
// generic override for service library generation
  template: service library init

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
// generic override for service library generation
  template: service library init

  node: ${node.name}
  options.projectName:${options.projectName}

node.id:${node.id}
node.name:${node.name}
node.kind.name:${node.kind.name}
node.kind.ordinal:${node.kind.ordinal}
node.children.size:${node.children.size}
node.incomingLinks:${node.incomingLinks}
node.incomingLinks:${'$'}{node.incomingLinks.first().value}
node.isComposite:${node.isComposite}
node.isLink:${node.isLink}
node.isTerminal:${node.isTerminal}
node.layout:${node.layout}
node.link.sourceNodeId:${node.link?.sourceNodeId}
node.link.targetNodeId:${node.link?.targetNodeId}
node.link.sourcePortName:${node.link?.sourcePortName}
node.link.targetPortName:${node.link?.targetPortName}
node.metadata.size:${node.metadata.size}
node.metadata:${node.metadata}
node.outgoingLinks:${node.outgoingLinks}
node.parentId:${node.parentId}
node.pluginData:${node.pluginData}
node.ports:${node.ports}
node.pluginData:${node.pluginData}
node.text.initializationLanguageId:${node.text.initializationLanguageId}
node.text.initialization:${node.text.initialization}
node.text.sourceLanguageId:${node.text.sourceLanguageId}
node.text.source:${node.text.source}
node.text.specificationLanguageId:${node.text.specificationLanguageId}
node.text.specification:${node.text.specification}
node.text.aiInstructionsLanguageId:${node.text.aiInstructionsLanguageId}
node.text.aiInstructions:${node.text.aiInstructions}
node.text.testsLanguageId:${node.text.testsLanguageId}
node.text.tests:${node.text.tests}
""".trimIndent()


    override fun getTransport(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getErrorPipe(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getDependencyInjection(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getTest(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getTestSuite(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getInputPort(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getOutputPort(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getStaticFile(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()


    override fun getCompilerTemplate(document: InflowDocument, node: Node, options: CompilerOptions): String =
        """

""".trimIndent()
}
