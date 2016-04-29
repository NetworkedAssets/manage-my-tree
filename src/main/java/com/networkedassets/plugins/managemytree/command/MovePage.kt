package com.networkedassets.plugins.managemytree.command

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName

@JsonTypeName("movePage")
class MovePage(val pageId: String, val newParentId: String?, val newPosition: Int?) : Command() {
    var movedPage: OriginalPage? = null
    var name: String? = null

    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        val page = pageManager.getPage(pageId, ec)

        if (!ec.canEdit(page))
            throw PermissionException("""Cannot move page "${page.title}": insufficient permissions!""")

        movedPage = OriginalPage(page.id, Location(getTruePosition(page), page.parent.id))
        name = page.title

        moveAsChildIfNecessary(page, newParentId, pageManager, ec)
        pageManager.setPagePosition(page, newPosition)
    }

    private fun getTruePosition(page: Page): Int {
        var i = 0;
        for (child in page.parent.sortedChildren) {
            if (child == page) return i
            i++
        }
        throw IllegalArgumentException("Page is not in its parent's children list")
    }

    private fun moveAsChildIfNecessary(page: Page, newParentId: String?, pageManager: PageManager, ec: ExecutionContext) {
        if (newParentId == null) return
        val newParent = pageManager.getPage(newParentId, ec)

        if (page.parent != newParent) {
            pageManager.movePageAsChild(page, newParent)
        }

        assert(page.parent == newParent)
    }

    override fun revert(pageManager: PageManager) {
        if (movedPage == null) throw IllegalStateException("Cannot revert changes before applying them")
        val (pageId, location) = movedPage!!
        val (position, parentId) = location
        val page = pageManager.getPage(pageId) ?: throw IllegalStateException("No page with id=$pageId found")
        val oldParent = pageManager.getPage(parentId) ?: throw IllegalStateException("No page with id=$pageId found")

        if (page.parent != oldParent) {
            pageManager.movePageAsChild(page, oldParent)
        }

        pageManager.setPagePosition(page, position)
    }

    //region equals, hashCode, toString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as MovePage

        if (pageId != other.pageId) return false
        if (newParentId != other.newParentId) return false
        if (newPosition != other.newPosition) return false
        if (movedPage != other.movedPage) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pageId.hashCode()
        result += 31 * result + (newParentId?.hashCode() ?: 0)
        result += 31 * result + (newPosition ?: 0)
        result += 31 * result + (movedPage?.hashCode() ?: 0)
        result += 31 * result + (name?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "MovePage(pageId='$pageId', newParentId=$newParentId, newPosition=$newPosition, movedPage=$movedPage, name=$name)"
    }

    //endregion

    companion object {
        @JvmStatic @JsonCreator fun movePage(
                @JsonProperty("pageId") pageId: String,
                @JsonProperty("newParentId") newParentId: String?,
                @JsonProperty("newPosition") newPosition: Int?,
                @JsonProperty("movedPage") movedPage: OriginalPage?,
                @JsonProperty("name") name: String?
        ) = MovePage(pageId, newParentId, newPosition).apply { this.movedPage = movedPage; this.name = name }
    }
}