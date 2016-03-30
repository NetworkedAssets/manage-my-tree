package com.networkedassets.plugins.addpagetree.managepages

import com.atlassian.confluence.api.service.exceptions.PermissionException
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.spaces.Space
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.ConfluenceUser
import org.codehaus.jackson.map.annotate.JsonDeserialize
import org.codehaus.jackson.map.annotate.JsonSerialize
import java.util.*

/**
 * Used to store mappings from jstree ids to page ids needed in single command list execution
 */
// for people not familiar with Kotlin:
//  "constructor(...)" after the class name is the default constructor
//  "constructor" keyword is optional, unless you want to use annotations
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
    fun canEdit(p: Page)   = permissionManager.hasPermission(user, Permission.EDIT, p)
}

@JsonDeserialize(using = ManagePagesCommandDeserializer::class)
sealed class Command : (PageManager, ExecutionContext) -> Unit {
    abstract fun execute(pageManager: PageManager, ec: ExecutionContext)
    abstract fun revert(pageManager: PageManager, ec: ExecutionContext)
    override fun invoke(pageManager: PageManager, ec: ExecutionContext) = execute(pageManager, ec)

    @JsonSerialize(using = AddPageSerializer::class)
    class AddPage(val name: String, val newPageJstreeId: String, val parentId: String) : Command() {
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
        }

        override fun revert(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(newPageJstreeId, ec)
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

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result += 31 * result + newPageJstreeId.hashCode()
            result += 31 * result + parentId.hashCode()
            return result
        }

        override fun toString(): String {
            return "AddPage(name='$name', newPageJstreeId='$newPageJstreeId', parentId='$parentId')"
        }
        //endregion
    }

    @JsonSerialize(using = RemovePageSerializer::class)
    class RemovePage(val pageId: String) : Command() {
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)
            removePage(page, pageManager, ec)
        }

        private fun removePage(page: Page, pageManager: PageManager, ec: ExecutionContext) {
            if (!ec.canRemove(page))
                throw PermissionException("""Cannot remove page "${page.title}": insufficient permissions!""")

            val children = ArrayList(page.children)
            children.forEach { removePage(it, pageManager, ec) }
            pageManager.trashPage(page) // TODO: save info about all the trashed pages and their locations
        }

        override fun revert(pageManager: PageManager, ec: ExecutionContext) {
            throw UnsupportedOperationException() // TODO: restore all the trashed pages to their correct locations
        }

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as RemovePage

            if (pageId != other.pageId) return false

            return true
        }

        override fun hashCode(): Int {
            return pageId.hashCode()
        }

        override fun toString(): String {
            return "RemovePage(pageId=$pageId)"
        }
        //endregion
    }

    @JsonSerialize(using = MovePageSerializer::class)
    class MovePage(val pageId: String, val newParentId: String?, val newPosition: Int?) : Command() {
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)

            if (!ec.canEdit(page))
                throw PermissionException("""Cannot move page "${page.title}": insufficient permissions!""")

            moveAsChildIfNecessary(page, newParentId, pageManager, ec)
            moveInHierarchyIfNecessary(page, newPosition, pageManager)
        }

        private fun moveAsChildIfNecessary(page: Page, newParentId: String?, pageManager: PageManager, ec: ExecutionContext) {
            if (newParentId != null) {
                val newParent = pageManager.getPage(newParentId, ec)

                if (page.parent != newParent) {
                    pageManager.movePageAsChild(page, newParent)
                }

                assert(page.parent == newParent)
            }
        }

        private fun moveInHierarchyIfNecessary(page: Page, newPosition: Int?, pageManager: PageManager) {
            if (newPosition != null) {
                if (newPosition == page.position) return

                val children = page.parent.sortedChildren.filter { it != page }

                if (children.isEmpty()) return

                when (newPosition) {
                    0 -> pageManager.movePageBefore(page, children[0])
                    else -> pageManager.movePageAfter(page, children[newPosition - 1])
                }
            }
        }

        override fun revert(pageManager: PageManager, ec: ExecutionContext) {
            throw UnsupportedOperationException() // TODO: move page to old parent and old position
        }

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MovePage

            if (pageId != other.pageId) return false
            if (newParentId != other.newParentId) return false
            if (newPosition != other.newPosition) return false

            return true
        }

        override fun hashCode(): Int {
            var result = pageId.hashCode()
            result += 31 * result + (newParentId?.hashCode() ?: 0)
            result += 31 * result + (newPosition ?: 0)
            return result
        }

        override fun toString(): String {
            return "MovePage(pageId=$pageId, newParentId=$newParentId, newPosition=$newPosition)"
        }
        //endregion
    }

    @JsonSerialize(using = RenamePageSerializer::class)
    class RenamePage(val pageId: String, val newName: String) : Command() {
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)

            if (!ec.canEdit(page))
                throw PermissionException("""Cannot rename page "${page.title}": insufficient permissions!""")

            if (page.title != newName)
                pageManager.renamePage(page, newName)
        }

        override fun revert(pageManager: PageManager, ec: ExecutionContext) {
            throw UnsupportedOperationException() // TODO: rename back to the old name
        }

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as RenamePage

            if (pageId != other.pageId) return false
            if (newName != other.newName) return false

            return true
        }

        override fun hashCode(): Int {
            var result = pageId.hashCode()
            result += 31 * result + newName.hashCode()
            return result
        }

        override fun toString(): String {
            return "RenamePage(pageId=$pageId, newName='$newName')"
        }
        //endregion
    }
}

fun PageManager.getPage(s: String, ec: ExecutionContext): Page {
    val e = IllegalArgumentException("No page with id=$s found")
    val id = (if (s.startsWith("j")) ec[s] else s.toLong()) ?: throw e
    return this.getPage(id) ?: throw e
}