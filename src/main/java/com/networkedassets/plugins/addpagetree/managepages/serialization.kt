package com.networkedassets.plugins.addpagetree.managepages

import org.codehaus.jackson.JsonGenerator
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.JsonParser
import org.codehaus.jackson.map.*
import org.codehaus.jackson.map.type.TypeFactory
import org.codehaus.jackson.node.JsonNodeFactory

class ManagePagesCommandDeserializer : JsonDeserializer<Command>() {
    companion object {
        private val objectMapper = ObjectMapper()
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Command? {
        val jtree = parser.readValueAsTree()
        val commandType = jtree["commandType"]?.textValue
        when (commandType) {
            "addPage" -> return addPage(jtree)
            "removePage" -> return removePage(jtree)
            "movePage" -> return movePage(jtree)
            "renamePage" -> return renamePage(jtree)
            else -> return null
        }
    }

    private fun addPage(jtree: JsonNode?): Command.AddPage? {
        if (jtree == null) return null

        var nameNode: JsonNode? = jtree["name"]
        nameNode = if (nameNode?.isTextual ?: false) nameNode else return null
        val name = nameNode!!.textValue

        var newPageJstreeIdNode: JsonNode? = jtree["newPageJstreeId"]
        newPageJstreeIdNode = if (newPageJstreeIdNode?.isTextual ?: false) newPageJstreeIdNode else return null
        val newPageJstreeId = newPageJstreeIdNode!!.textValue

        var parentIdNode: JsonNode? = jtree["parentId"]
        parentIdNode = if (parentIdNode?.isTextual ?: false) parentIdNode else return null
        val parentId = parentIdNode!!.textValue

        var confluenceIdNode: JsonNode? = jtree["confluenceId"]
        confluenceIdNode = if (confluenceIdNode?.isIntegralNumber ?: false) confluenceIdNode else null
        val confluenceId = confluenceIdNode?.longValue

        val addCmd = Command.AddPage(name, newPageJstreeId, parentId)
        addCmd.confluenceId = confluenceId

        return addCmd
    }

    private fun removePage(jtree: JsonNode?): Command.RemovePage? {
        if (jtree == null) return null

        var pageIdNode: JsonNode? = jtree["pageId"]
        pageIdNode = if (pageIdNode?.isTextual ?: false) pageIdNode else return null
        val pageId = pageIdNode!!.textValue
        val cmd = Command.RemovePage(pageId)

        val removedPagesNode: JsonNode? = jtree["removedPages"]
        val removedPages =
                if (removedPagesNode != null) objectMapper.readValue(removedPagesNode,
                        TypeFactory.defaultInstance().constructCollectionType(
                                List::class.java, OriginalPage::class.java))
                else arrayListOf<OriginalPage>()
        cmd.removedPages += removedPages

        val nameNode: JsonNode? = jtree["name"]
        cmd.name = if (nameNode != null && nameNode.isTextual) nameNode.textValue else null

        return cmd
    }

    private fun movePage(jtree: JsonNode?): Command.MovePage? {
        if (jtree == null) return null

        var pageIdNode: JsonNode? = jtree["pageId"]
        pageIdNode = if (pageIdNode?.isTextual ?: false) pageIdNode else return null
        val pageId = pageIdNode!!.textValue

        var newParentNode: JsonNode? = jtree["newParentId"]
        newParentNode = if (newParentNode?.isTextual ?: false) newParentNode else null
        val newParentId = newParentNode?.textValue

        var newPosNode: JsonNode? = jtree["newPosition"]
        newPosNode = if (newPosNode?.isIntegralNumber ?: false) newPosNode else null
        val newPosition = newPosNode?.intValue

        var movedPageNode: JsonNode? = jtree["movedPage"]
        val movedPage =
                if (movedPageNode != null) objectMapper.readValue(movedPageNode, OriginalPage::class.java)
                else null

        val movCmd = Command.MovePage(pageId, newParentId, newPosition)
        movCmd.movedPage = movedPage

        movCmd.name = jtree["name"].let { if (it != null && it.isTextual) it.textValue else null }

        return movCmd
    }

    private fun renamePage(jtree: JsonNode?): Command.RenamePage? {
        if (jtree == null) return null

        var pageIdNode: JsonNode? = jtree["pageId"]
        pageIdNode = if (pageIdNode?.isTextual ?: false) pageIdNode else return null
        val pageId = pageIdNode!!.textValue

        val newName = jtree["newName"]?.textValue ?: return null

        var renamedPageIdNode: JsonNode? = jtree["renamedPageId"]
        renamedPageIdNode = if (renamedPageIdNode?.isIntegralNumber ?: false) renamedPageIdNode else null
        val renamedPageId = renamedPageIdNode?.longValue

        var oldNameNode: JsonNode? = jtree["oldName"]
        oldNameNode = if (oldNameNode?.isTextual ?: false) oldNameNode else null
        var oldName = oldNameNode?.textValue

        val renCmd = Command.RenamePage(pageId, newName)
        renCmd.oldName = oldName
        renCmd.renamedPageId = renamedPageId

        return renCmd
    }

}

class AddPageSerializer : JsonSerializer<Command.AddPage>() {
    override fun serialize(
            command: Command.AddPage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val addCmd = JsonNodeFactory.instance.objectNode()
        addCmd.put("commandType", "addPage")
        addCmd.put("name", command.name)
        addCmd.put("newPageJstreeId", command.newPageJstreeId)
        addCmd.put("parentId", command.parentId)
        addCmd.put("confluenceId", command.confluenceId)
        jgen.writeTree(addCmd)
    }
}

class RemovePageSerializer : JsonSerializer<Command.RemovePage>() {
    override fun serialize(
            command: Command.RemovePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val remCmd = JsonNodeFactory.instance.objectNode()
        remCmd.put("commandType", "removePage")
        remCmd.put("pageId", command.pageId)
        remCmd.put("removedPages", JsonNodeFactory.instance.POJONode(command.removedPages))
        remCmd.put("name", command.name)
        jgen.writeTree(remCmd)
    }
}

class MovePageSerializer : JsonSerializer<Command.MovePage>() {
    override fun serialize(
            command: Command.MovePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val movCmd = JsonNodeFactory.instance.objectNode()
        movCmd.put("commandType", "movePage")
        movCmd.put("pageId", command.pageId)
        movCmd.put("newParentId", command.newParentId)
        movCmd.put("newPosition", command.newPosition)
        movCmd.put("movedPage", JsonNodeFactory.instance.POJONode(command.movedPage))
        movCmd.put("name", command.name)
        jgen.writeTree(movCmd)
    }
}

class RenamePageSerializer : JsonSerializer<Command.RenamePage>() {
    override fun serialize(
            command: Command.RenamePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val renCmd = JsonNodeFactory.instance.objectNode()
        renCmd.put("commandType", "renamePage")
        renCmd.put("pageId", command.pageId)
        renCmd.put("newName", command.newName)
        renCmd.put("renamedPageId", command.renamedPageId)
        renCmd.put("oldName", command.oldName)
        jgen.writeTree(renCmd)
    }
}