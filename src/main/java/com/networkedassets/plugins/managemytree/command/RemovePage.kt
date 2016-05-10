package com.networkedassets.plugins.managemytree.command

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.AbstractPage
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.annotate.JsonTypeName
import java.util.*

@JsonTypeName("removePage")
class RemovePage(val pageId: String) : Command() {
    val removedPages: MutableList<OriginalPage> = mutableListOf()
    var name: String? = null

    override fun execute(pageManager: PageManager, ec: ExecutionContext) {
        val page = pageManager.getAbstractPage(pageId, ec)
        name = page.title
        removePage(page, pageManager, ec)
    }

    private fun removePage(page: AbstractPage, pageManager: PageManager, ec: ExecutionContext) {
        if (!ec.canRemove(page))
            throw PermissionException("""Cannot remove page "${page.title}": insufficient permissions!""")

        if (page is Page) {
            val children = ArrayList(page.sortedChildren).asReversed()
            children.forEach { removePage(it, pageManager, ec) }


            removedPages += OriginalPage(page.id, Location(page.truePosition, page.parent.id))
        }

        pageManager.trashPage(page)
    }

    override fun revert(pageManager: PageManager) {
        for ((pageId, location) in removedPages.asReversed()) {
            val (position, parentId) = location
            val page = pageManager.getPage(pageId) ?: throw IllegalArgumentException("No page with id=$pageId found")
            val parent = pageManager.getPage(parentId)
            pageManager.restorePage(page)
            pageManager.movePageAsChild(page, parent)
            pageManager.setPagePosition(page, position)
        }
    }

    //region equals, hashCode, toString

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other?.javaClass != javaClass) return false

        other as RemovePage

        if (pageId != other.pageId) return false
        if (removedPages != other.removedPages) return false
        if (name != other.name) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pageId.hashCode()
        result += 31 * result + removedPages.hashCode()
        result += 31 * result + (name?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "RemovePage(pageId='$pageId', removedPages=$removedPages, name=$name)"
    }

    //endregion

    companion object {
        @Suppress("unused")
        @JvmStatic @JsonCreator fun removePage(
                @JsonProperty("pageId") pageId: String,
                @JsonProperty("removedPages") removedPages: MutableList<OriginalPage>?,
                @JsonProperty("name") name: String?
        ) = RemovePage(pageId).apply { if (removedPages != null) this.removedPages += removedPages; this.name = name }
    }
}