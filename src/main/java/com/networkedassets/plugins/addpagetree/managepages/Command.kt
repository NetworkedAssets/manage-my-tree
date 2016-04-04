package com.networkedassets.plugins.addpagetree.managepages

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.spaces.Space
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.ConfluenceUser
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.map.annotate.JsonDeserialize
import org.codehaus.jackson.map.annotate.JsonSerialize
import java.util.*

/**
 * Used to store mappings from jstree ids to page ids needed in single command list execution
 */
// for people not familiar with Kotlin:
//  "constructor(...)" after the class name is the default constructor
//  "constructor" keyword is optional, unless you want to use annotations on constructor
//  "val" and "var" in constructor params mean that the class has a property (field + getter + (if var) setter) that has the same name
//  "@JvmOverloads" allows you to use default parameters in other languages (i.e. Java)
//  ":" means "implements"/"extends" from Java
//  "by m" generates implementations for all the methods in the interface/class that delegate to m
class ExecutionContext
@JvmOverloads constructor(
        val permissionManager: PermissionManager,
        val space: Space,
        val idMapping: MutableMap<String, Long> = HashMap())
: MutableMap<String, Long> by idMapping {
    val user: ConfluenceUser
        get() = AuthenticatedUserThreadLocal.get()

    fun canCreate(p: Page) = permissionManager.hasCreatePermission(user, space, p)
    fun canRemove(p: Page) = permissionManager.hasPermission(user, Permission.REMOVE, p)
    fun canEdit(p: Page) = permissionManager.hasPermission(user, Permission.EDIT, p)
}

data class Location
@JsonCreator constructor(
        @param:JsonProperty("position") val position: Int?,
        @param:JsonProperty("parentId") val parentId: Long)

data class OriginalPage
@JsonCreator constructor(
        @param:JsonProperty("pageId") val pageId: Long,
        @param:JsonProperty("originalLocation") val originalLocation: Location)

@JsonDeserialize(using = ManagePagesCommandDeserializer::class)
sealed class Command : (PageManager, ExecutionContext) -> Unit {
    abstract fun execute(pageManager: PageManager, ec: ExecutionContext)
    abstract fun revert(pageManager: PageManager)
    override fun invoke(pageManager: PageManager, ec: ExecutionContext) = execute(pageManager, ec)

    @JsonSerialize(using = AddPageSerializer::class)
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
    }

    @JsonSerialize(using = RemovePageSerializer::class)
    class RemovePage(val pageId: String) : Command() {
        val removedPages: MutableList<OriginalPage> = mutableListOf()

        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)
            removePage(page, pageManager, ec)
        }

        private fun removePage(page: Page, pageManager: PageManager, ec: ExecutionContext) {
            if (!ec.canRemove(page))
                throw PermissionException("""Cannot remove page "${page.title}": insufficient permissions!""")

            val children = ArrayList(page.sortedChildren).asReversed()
            children.forEach { removePage(it, pageManager, ec) }

            removedPages += OriginalPage(page.id, Location(page.position, page.parent.id))
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
        override fun hashCode(): Int {
            var result = pageId.hashCode()
            result += 31 * result + removedPages.hashCode()
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as RemovePage

            if (pageId != other.pageId) return false
            if (removedPages != other.removedPages) return false

            return true
        }

        override fun toString(): String {
            return "RemovePage(pageId='$pageId', removedPages=$removedPages)"
        }
        //endregion
    }

    @JsonSerialize(using = MovePageSerializer::class)
    class MovePage(val pageId: String, val newParentId: String?, val newPosition: Int?) : Command() {
        var movedPage: OriginalPage? = null;

        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)

            if (!ec.canEdit(page))
                throw PermissionException("""Cannot move page "${page.title}": insufficient permissions!""")

            movedPage = OriginalPage(page.id, Location(getTruePosition(page), page.parent.id))

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

            return true
        }

        override fun hashCode(): Int {
            var result = pageId.hashCode()
            result += 31 * result + (newParentId?.hashCode() ?: 0)
            result += 31 * result + (newPosition ?: 0)
            result += 31 * result + (movedPage?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "MovePage(pageId='$pageId', newParentId=$newParentId, newPosition=$newPosition, movedPage=$movedPage)"
        }
        //endregion
    }

    @JsonSerialize(using = RenamePageSerializer::class)
    class RenamePage(val pageId: String, val newName: String) : Command() {
        var renamedPageId: Long? = null
        var originalPageName: String? = null
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)

            if (!ec.canEdit(page))
                throw PermissionException("""Cannot rename page "${page.title}": insufficient permissions!""")

            renamedPageId = page.id
            originalPageName = page.title

            if (page.title != newName)
                pageManager.renamePage(page, newName)
        }

        override fun revert(pageManager: PageManager) {
            if (renamedPageId == null || originalPageName == null)
                throw IllegalStateException("Cannot revert changes before applying them")
            val page = pageManager.getPage(renamedPageId!!) ?:
                    throw IllegalStateException("No page with id=$pageId found")
            pageManager.renamePage(page, originalPageName)
        }

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as RenamePage

            if (pageId != other.pageId) return false
            if (newName != other.newName) return false
            if (renamedPageId != other.renamedPageId) return false
            if (originalPageName != other.originalPageName) return false

            return true
        }

        override fun hashCode(): Int {
            var result = pageId.hashCode()
            result += 31 * result + newName.hashCode()
            result += 31 * result + (renamedPageId?.hashCode() ?: 0)
            result += 31 * result + (originalPageName?.hashCode() ?: 0)
            return result
        }

        override fun toString(): String {
            return "RenamePage(pageId='$pageId', newName='$newName', renamedPageId=$renamedPageId, originalPageName=$originalPageName)"
        }
        //endregion
    }
}

fun PageManager.getPage(s: String, ec: ExecutionContext): Page {
    val e = IllegalArgumentException("No page with id=$s found")
    val id = (if (s.startsWith("j")) ec[s] else s.toLong()) ?: throw e
    return this.getPage(id) ?: throw e
}

fun PageManager.setPagePosition(page: Page, newPosition: Int?) {
    if (newPosition == null || newPosition == page.position) return
    val children = page.parent.sortedChildren.filter { it != page }
    if (children.isEmpty()) return

    when (newPosition) {
        0 -> movePageBefore(page, children[0])
        else -> movePageAfter(page, children[newPosition - 1])
    }
}