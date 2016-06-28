package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.*
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("insertTemplate")
class InsertTemplate(
        val parentId: String,
        val templateId: TemplateId,
        val newPageJstreeIds: Map<String, String>
) : Command() {
    var insertedPages: MutableList<Long> = mutableListOf()
    var name: String? = null

    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        when (templateId) {
            is TemplateId.Custom -> executeTemplate(templateId.getFromDb(), pageManager, ec)
            is TemplateId.FromBlueprint -> executeTemplate(templateId.getFromBlueprint()!!, pageManager, ec)
        }
    }

    private fun executeTemplate(template: Template, pageManager: PageManager, ec: ExecutionContext) {
        val parent = pageManager.getPage(parentId, ec)
        name = template.name

        for (outline in template.outlines)
            plantOutline(outline, parent, pageManager, ec)
    }

    private fun plantOutline(outline: Outline, parent: Page, pageManager: PageManager, ec: ExecutionContext) {
        val page = Page()
        page.title = outline.title
        page.bodyAsString = outline.text;
        page.space = parent.space
        parent.addChild(page)
        page.version = 1

        if (!ec.canCreate(page))
            throw PermissionException("""Cannot create page "${page.title}": insufficient permissions! """)

        pageManager.saveContentEntity(page, null)
        ec[newPageJstreeIds[outline.id.toString()]!!] = page.id
        insertedPages.add(page.id)

        for (child in outline.children)
            plantOutline(child, page, pageManager, ec)
    }

    override fun revert(pageManager: PageManager) {
        // trash latest added first
        insertedPages.asReversed().forEach { pageManager.trashPage(pageManager.getPage(it)) }
    }

    //region equals, hashcode, toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InsertTemplate

        if (parentId != other.parentId) return false
        if (templateId != other.templateId) return false
        if (newPageJstreeIds != other.newPageJstreeIds) return false
        if (insertedPages != other.insertedPages) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parentId.hashCode()
        result = 31 * result + templateId.hashCode()
        result = 31 * result + newPageJstreeIds.hashCode()
        result = 31 * result + insertedPages.hashCode()
        return result
    }

    override fun toString(): String {
        return "InsertTemplate(parentId='$parentId', templateId=$templateId, newPageJstreeIds=$newPageJstreeIds, insertedPages=$insertedPages)"
    }
    //endregion

    companion object {
        @JvmStatic @JsonCreator fun insertTemplate(
                @JsonProperty("parentId") parentId: String,
                @JsonProperty("templateId") templateId: TemplateId,
                @JsonProperty("insertedPages") insertedPages: List<Long>?,
                @JsonProperty("newPageJstreeIds") newPageJstreeIds: Map<String, String>,
                @JsonProperty("name") name: String?
        ) = InsertTemplate(parentId, templateId, newPageJstreeIds)
                .apply { this.insertedPages = insertedPages?.toMutableList() ?: mutableListOf(); this.name = name }
    }
}
