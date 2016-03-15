package com.networkedassets.plugins.addpagetree.managepages

import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import org.codehaus.jackson.map.annotate.JsonDeserialize
import org.codehaus.jackson.map.annotate.JsonSerialize

@JsonDeserialize(using = ManagePagesCommandDeserializer::class)
sealed class Command : (PageManager) -> Unit {
    abstract fun execute(pageManager: PageManager)
    override fun invoke(pageManager: PageManager) = execute(pageManager)

    @JsonSerialize(using = AddPageSerializer::class)
    class AddPage(val name: String, val parentId: Long) : Command() {
        override fun execute(pageManager: PageManager) {
            val parent = pageManager.getPage(parentId) ?: throw IllegalArgumentException("No page with id=$parentId found")
            val page = Page()
            page.title = name
            page.space = parent.space
            page.setParentPage(parent)
            parent.addChild(page)
            page.version = 1
            pageManager.saveContentEntity(page, null)
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as AddPage

            if (name != other.name) return false
            if (parentId != other.parentId) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result += 31 * result + parentId.hashCode()
            return result
        }

        override fun toString(): String {
            return "AddPage(name='$name', parentId=$parentId)"
        }
    }

    @JsonSerialize(using = RemovePageSerializer::class)
    class RemovePage(val pageId: Long) : Command() {
        override fun execute(pageManager: PageManager) {
            val page = pageManager.getPage(pageId) ?: throw IllegalArgumentException("No page with id=$pageId found")
            removePage(page, pageManager)
        }

        private fun removePage(page: Page, pageManager: PageManager) {
            page.children.forEach { removePage(it, pageManager) }
            pageManager.trashPage(page)
        }

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
    }

    @JsonSerialize(using = MovePageSerializer::class)
    class MovePage(val pageId: Long, val newParentId: Long?, val newPosition: Int?) : Command() {
        override fun execute(pageManager: PageManager) {
            val page = pageManager.getPage(pageId) ?: throw IllegalArgumentException("No page with id=$pageId found")

            moveAsChildIfNecessary(page, newParentId, pageManager)
            moveInHierarchyIfNecessary(page, newPosition, pageManager)
        }

        private fun moveAsChildIfNecessary(page: Page, newParentId: Long?, pageManager: PageManager) {
            if (newParentId != null) {
                val newParent = pageManager.getPage(newParentId) ?:
                        throw IllegalArgumentException("No page with id=$pageId found")

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
                    0 -> pageManager.movePageBefore(page, children[1])
                    else -> pageManager.movePageAfter(page, children[newPosition - 1])
                }
            }
        }

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
    }

    @JsonSerialize(using = RenamePageSerializer::class)
    class RenamePage(val pageId: Long, val newName: String) : Command() {
        override fun execute(pageManager: PageManager) {
            val page = pageManager.getPage(pageId) ?: throw IllegalArgumentException("No page with id=$pageId found")

            if (page.title != newName)
                pageManager.renamePage(page, newName)
        }

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
    }
}