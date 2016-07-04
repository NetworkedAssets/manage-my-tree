package com.networkedassets.plugins.managemytree.commands

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.PageManager
import com.networkedassets.plugins.managemytree.Command
import com.networkedassets.plugins.managemytree.ExecutionContext
import com.networkedassets.plugins.managemytree.getAbstractPage
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("renamePage")
class RenamePage(val pageId: String, val newName: String) : Command() {
    var renamedPageId: Long? = null
    var oldName: String? = null
    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        val page = pageManager.getAbstractPage(pageId, ec)

        if (!ec.canEdit(page))
            throw PermissionException("""Cannot rename page "${page.title}": insufficient permissions!""")

        renamedPageId = page.id
        oldName = page.title

        if (page.title != newName)
            pageManager.renamePage(page, newName)
    }

    override fun revert(pageManager: PageManager) {
        if (renamedPageId == null || oldName == null)
            throw IllegalStateException("Cannot revert changes before applying them")
        val page = pageManager.getPage(renamedPageId!!) ?:
                throw IllegalStateException("No page with id=$pageId found")
        pageManager.renamePage(page, oldName)
    }

    //region equals, hashCode, toString
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RenamePage

        if (pageId != other.pageId) return false
        if (newName != other.newName) return false
        if (renamedPageId != other.renamedPageId) return false
        if (oldName != other.oldName) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pageId.hashCode()
        result += 31 * result + newName.hashCode()
        result += 31 * result + (renamedPageId?.hashCode() ?: 0)
        result += 31 * result + (oldName?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "RenamePage(pageId='$pageId', newName='$newName', renamedPageId=$renamedPageId, originalPageName=$oldName)"
    }
    //endregion

    companion object {
        @JvmStatic @JsonCreator fun renamePage(
                @JsonProperty("pageId") pageId: String,
                @JsonProperty("newName") newName: String,
                @JsonProperty("renamedPageId") renamedPageId: Long?,
                @JsonProperty("oldName") oldName: String?
        ) = RenamePage(pageId, newName).apply { this.renamedPageId = renamedPageId; this.oldName = oldName }
    }
}