package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.Command
import com.networkedassets.plugins.managemytree.ExecutionContext
import org.codehaus.jackson.annotate.*

@JsonTypeName("insertTemplate")
class InsertTemplate(val parentId: String, val template: Template) : Command() {
    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        println("spaceblueprintmanager: " + ec.spaceBlueprintManager)
        println("blueprintcontentgenerator: " + ec.blueprintContentGenerator)
    }

    override fun revert(pageManager: PageManager) {
        throw UnsupportedOperationException()
    }

    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InsertTemplate

        if (parentId != other.parentId) return false
        if (template != other.template) return false

        return true
    }

    override fun hashCode(): Int{
        var result = parentId.hashCode()
        result = 31 * result + template.hashCode()
        return result
    }

    companion object {
        @JvmStatic @JsonCreator fun insertTemplate(
                @JsonProperty("parentId") parentId: String,
                @JsonProperty("template") template: Template
        ) = InsertTemplate(parentId, template)
    }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "templateType")
@JsonSubTypes(
        JsonSubTypes.Type(Template.FromBlueprint::class, name = "fromBlueprint"),
        JsonSubTypes.Type(Template.Custom::class, name = "custom")
)
@JsonIgnoreProperties(ignoreUnknown = true)
sealed class Template {
    @JsonTypeName("fromBlueprint")
    class FromBlueprint
    @JsonCreator constructor(
            @param:JsonProperty("spaceBlueprintId") val spaceBlueprintId: String
    ) : Template() {
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as FromBlueprint

            if (spaceBlueprintId != other.spaceBlueprintId) return false

            return true
        }

        override fun hashCode(): Int{
            return spaceBlueprintId.hashCode()
        }
    }

    @JsonTypeName("custom")
    class Custom
    @JsonCreator constructor(
            @param:JsonProperty("customOutlineId") val customOutlineId: Int
    ) : Template() {
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Custom

            if (customOutlineId != other.customOutlineId) return false

            return true
        }

        override fun hashCode(): Int{
            return customOutlineId
        }
    }
}