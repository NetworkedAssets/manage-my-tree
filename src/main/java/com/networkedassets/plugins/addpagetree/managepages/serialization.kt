package com.networkedassets.plugins.addpagetree.managepages

import org.codehaus.jackson.JsonGenerator
import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.JsonParser
import org.codehaus.jackson.map.DeserializationContext
import org.codehaus.jackson.map.JsonDeserializer
import org.codehaus.jackson.map.JsonSerializer
import org.codehaus.jackson.map.SerializerProvider
import org.codehaus.jackson.node.JsonNodeFactory

class ManagePagesCommandDeserializer : JsonDeserializer<Command>() {

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


        return Command.AddPage(name, newPageJstreeId, parentId)
    }

    private fun removePage(jtree: JsonNode?): Command.RemovePage? {
        if (jtree == null) return null

        var pageIdNode: JsonNode? = jtree["pageId"]
        pageIdNode = if (pageIdNode?.isTextual ?: false) pageIdNode else return null
        val pageId = pageIdNode!!.textValue

        return Command.RemovePage(pageId)
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

        return Command.MovePage(pageId, newParentId, newPosition)
    }

    private fun renamePage(jtree: JsonNode?): Command.RenamePage? {
        if (jtree == null) return null

        var pageIdNode: JsonNode? = jtree["pageId"]
        pageIdNode = if (pageIdNode?.isTextual ?: false) pageIdNode else return null
        val pageId = pageIdNode!!.textValue

        val newName = jtree["newName"]?.textValue ?: return null

        return Command.RenamePage(pageId, newName)
    }

}

class AddPageSerializer : JsonSerializer<Command.AddPage>() {
    override fun serialize(
            command: Command.AddPage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val addComm = JsonNodeFactory.instance.objectNode()
        addComm.put("commandType", "addPage")
        addComm.put("name", command.name)
        addComm.put("newPageJstreeId", command.newPageJstreeId)
        addComm.put("parentId", command.parentId)
        jgen.writeTree(addComm)
    }
}

class RemovePageSerializer : JsonSerializer<Command.RemovePage>() {
    override fun serialize(
            command: Command.RemovePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val addComm = JsonNodeFactory.instance.objectNode()
        addComm.put("commandType", "removePage")
        addComm.put("pageId", command.pageId)
        jgen.writeTree(addComm)
    }
}

class MovePageSerializer : JsonSerializer<Command.MovePage>() {
    override fun serialize(
            command: Command.MovePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val addComm = JsonNodeFactory.instance.objectNode()
        addComm.put("commandType", "movePage")
        addComm.put("pageId", command.pageId)
        addComm.put("newParentId", command.newParentId)
        addComm.put("newPosition", command.newPosition)
        jgen.writeTree(addComm)
    }
}

class RenamePageSerializer : JsonSerializer<Command.RenamePage>() {
    override fun serialize(
            command: Command.RenamePage,
            jgen: JsonGenerator,
            serializerProvider: SerializerProvider?) {
        val addComm = JsonNodeFactory.instance.objectNode()
        addComm.put("commandType", "renamePage")
        addComm.put("pageId", command.pageId)
        addComm.put("newName", command.newName)
        jgen.writeTree(addComm)
    }
}