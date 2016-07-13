package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.*
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("insertTemplatePart")
class InsertTemplatePart(
        val parentId: String,
        val templatePartId: TemplatePartId,
        val newPageJstreeId: String,
        val position: Int?
) : Command() {
    var insertedPageId: Long? = null
    var name: String? = null

    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        val outline = templatePartId.getOutlineWithoutChildren()
        val parent = pageManager.getPage(parentId, ec)

        val page = Page()
        page.title = outline.title
        page.bodyAsString = outline.text
        page.space = parent.space
        parent.addChild(page)
        page.version = 1

        if (!ec.canCreate(page))
            throw PermissionException("""Cannot create page "${page.title}": insufficient permissions! """)

        pageManager.saveContentEntity(page, null)
        pageManager.setPagePosition(page, position)
        ec[newPageJstreeId] = page.id
        insertedPageId = page.id
    }

    override fun revert(pageManager: PageManager) {
        val pageId = insertedPageId ?: throw IllegalStateException("cannot revert action that hasn't been executed")
        pageManager.trashPage(pageManager.getPage(pageId))
    }

    //region equals, hashcode, toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as InsertTemplatePart

        if (parentId != other.parentId) return false
        if (templatePartId != other.templatePartId) return false
        if (newPageJstreeId != other.newPageJstreeId) return false
        if (position != other.position) return false
        if (insertedPageId != other.insertedPageId) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = parentId.hashCode()
        result = 31 * result + templatePartId.hashCode()
        result = 31 * result + newPageJstreeId.hashCode()
        result = 31 * result + (position ?: 0)
        result = 31 * result + (insertedPageId?.hashCode() ?: 0)
        result = 31 * result + (name?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "InsertTemplatePart(parentId='$parentId', templatePartId=$templatePartId, newPageJstreeId='$newPageJstreeId', position=$position, insertedPageId=$insertedPageId, name=$name)"
    }
    //endregion

    companion object {
        @JvmStatic @JsonCreator fun insertTemplatePart(
                @JsonProperty("parentId") parentId: String,
                @JsonProperty("templatePartId") templatePartId: TemplatePartId,
                @JsonProperty("insertedPageId") insertedPageId: Long?,
                @JsonProperty("newPageJstreeId") newPageJstreeId: String,
                @JsonProperty("name") name: String?,
                @JsonProperty("position") position: Int?
        ) = InsertTemplatePart(parentId, templatePartId, newPageJstreeId, position)
                .apply { this.insertedPageId = insertedPageId; this.name = name }
    }
}
