package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.Command
import com.networkedassets.plugins.managemytree.ExecutionContext
import com.networkedassets.plugins.managemytree.TemplateId
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("insertTemplate")
class InsertTemplate(val parentId: String, val templateId: TemplateId) : Command() {
    val insertedPages: MutableList<Long> = mutableListOf()

    override fun execute(pageManager: PageManager, ec: ExecutionContext) {

    }

    override fun revert(pageManager: PageManager) {
        // trash latest added first
        insertedPages.asReversed().forEach { pageManager.trashPage(pageManager.getPage(it)) }
    }

    //region equals, hashcode, toString
    override fun equals(other: Any?): Boolean{
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InsertTemplate

        if (parentId != other.parentId) return false
        if (templateId != other.templateId) return false

        return true
    }

    override fun hashCode(): Int{
        var result = parentId.hashCode()
        result = 31 * result + templateId.hashCode()
        return result
    }

    companion object {
        @JvmStatic @JsonCreator fun insertTemplate(
                @JsonProperty("parentId") parentId: String,
                @JsonProperty("template") templateId: TemplateId
        ) = InsertTemplate(parentId, templateId)
    }
    //endregion
}
