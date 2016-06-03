package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.Command
import com.networkedassets.plugins.managemytree.ExecutionContext
import com.networkedassets.plugins.managemytree.getPage
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("addPage")
class AddPage(val name: String, val newPageJstreeId: String, val parentId: String) : Command() {
    var confluenceId: Long? = null
    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        val parent = pageManager.getPage(parentId, ec)
        val page = Page()
        page.title = name
        page.space = parent.space
        page.setParentPage(parent)
        parent.addChild(page)
        page.version = 1

        if (!ec.canCreate(page))
            throw PermissionException("""Cannot create page "${page.title}": insufficient permissions!""")

        pageManager.saveContentEntity(page, null)

        ec[newPageJstreeId] = page.id
        confluenceId = page.id
    }

    override fun revert(pageManager: PageManager) {
        if (confluenceId == null) throw IllegalStateException("Cannot revert changes before applying them")
        val page = pageManager.getPage(confluenceId!!)
        pageManager.trashPage(page)
    }

    //region equals, hashCode, toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as AddPage

        if (name != other.name) return false
        if (newPageJstreeId != other.newPageJstreeId) return false
        if (parentId != other.parentId) return false
        if (confluenceId != other.confluenceId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result += 31 * result + newPageJstreeId.hashCode()
        result += 31 * result + parentId.hashCode()
        result += 31 * result + (confluenceId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "AddPage(name='$name', newPageJstreeId='$newPageJstreeId', parentId='$parentId', confluenceId=$confluenceId)"
    }
    //endregion

    companion object {
        @JvmStatic @JsonCreator fun addPage(
                @JsonProperty("name") name: String,
                @JsonProperty("newPageJstreeId") newPageJstreeId: String,
                @JsonProperty("parentId") parentId: String,
                @JsonProperty("confluenceId") confluenceId: Long?
        ) = AddPage(name, newPageJstreeId, parentId).apply { this.confluenceId = confluenceId }
    }
}