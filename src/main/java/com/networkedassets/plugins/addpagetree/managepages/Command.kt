package com.networkedassets.plugins.addpagetree.managepages

import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import org.codehaus.jackson.map.annotate.JsonDeserialize
import org.codehaus.jackson.map.annotate.JsonSerialize
import java.util.*

/**
 * Used to store mappings from jstree ids to page ids needed in single command list execution
 */
class ExecutionContext(val m: MutableMap<String, Long> = HashMap()): MutableMap<String, Long> by m

@JsonDeserialize(using = ManagePagesCommandDeserializer::class)
sealed class Command : (PageManager, ExecutionContext) -> Unit {
    abstract fun execute(pageManager: PageManager, ec: ExecutionContext)
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
            pageManager.saveContentEntity(page, null)

            ec[newPageJstreeId] = page.id
        }

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as AddPage

            if (name != other.name) return false
            if (newPageJstreeId != other.newPageJstreeId) return false
            if (parentId != other.parentId) return false

            return true
        }

        override fun hashCode(): Int{
            var result = name.hashCode()
            result += 31 * result + newPageJstreeId.hashCode()
            result += 31 * result + parentId.hashCode()
            return result
        }

        override fun toString(): String{
            return "AddPage(name='$name', newPageJstreeId='$newPageJstreeId', parentId='$parentId')"
        }
        //endregion
    }

    @JsonSerialize(using = RemovePageSerializer::class)
    class RemovePage(val pageId: String) : Command() {
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)
            removePage(page, pageManager)
        }

        private fun removePage(page: Page, pageManager: PageManager) {
            val children = ArrayList(page.children)
            children.forEach { removePage(it, pageManager) }
            pageManager.trashPage(page)
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

        //region equals, hashCode, toString
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as MovePage

            if (pageId != other.pageId) return false
            if (newParentId != other.newParentId) return false
            if (newPosition != other.newPosition) return false

            return true
        }

        override fun hashCode(): Int{
            var result = pageId.hashCode()
            result += 31 * result + (newParentId?.hashCode() ?: 0)
            result += 31 * result + (newPosition ?: 0)
            return result
        }

        override fun toString(): String{
            return "MovePage(pageId=$pageId, newParentId=$newParentId, newPosition=$newPosition)"
        }
        //endregion
    }

    @JsonSerialize(using = RenamePageSerializer::class)
    class RenamePage(val pageId: String, val newName: String) : Command() {
        override fun execute(pageManager: PageManager, ec: ExecutionContext) {
            val page = pageManager.getPage(pageId, ec)

            if (page.title != newName)
                pageManager.renamePage(page, newName)
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