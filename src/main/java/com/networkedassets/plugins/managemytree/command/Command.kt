package com.networkedassets.plugins.managemytree.command

import com.atlassian.confluence.pages.AbstractPage
import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.spaces.Space
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.user.ConfluenceUser
import org.codehaus.jackson.annotate.*
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

    fun canCreate(p: AbstractPage) = permissionManager.hasCreatePermission(user, space, p)
    fun canRemove(p: AbstractPage) = permissionManager.hasPermission(user, Permission.REMOVE, p)
    fun canEdit(p: AbstractPage) = permissionManager.hasPermission(user, Permission.EDIT, p)
}

data class Location
@JsonCreator constructor(
        @param:JsonProperty("position") val position: Int?,
        @param:JsonProperty("parentId") val parentId: Long
)

data class OriginalPage
@JsonCreator constructor(
        @param:JsonProperty("pageId") val pageId: Long,
        @param:JsonProperty("originalLocation") val originalLocation: Location
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "commandType")
@JsonSubTypes(
        JsonSubTypes.Type(AddPage::class, name = "addPage"),
        JsonSubTypes.Type(RemovePage::class, name = "removePage"),
        JsonSubTypes.Type(MovePage::class, name = "movePage"),
        JsonSubTypes.Type(RenamePage::class, name = "renamePage")
)
@JsonIgnoreProperties(ignoreUnknown = true)
abstract class Command : (PageManager, ExecutionContext) -> Unit {
    abstract fun execute(pageManager: PageManager, ec: ExecutionContext)
    abstract fun revert(pageManager: PageManager)
    override fun invoke(pageManager: PageManager, ec: ExecutionContext) = execute(pageManager, ec)

}

fun PageManager.getAbstractPage(s: String, ec: ExecutionContext): AbstractPage {
    val e = IllegalArgumentException("No page with id=$s found")
    val id = (if (s.startsWith("j")) ec[s] else s.toLong()) ?: throw e
    return this.getAbstractPage(id) ?: throw e
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

val Page.truePosition: Int
    get() {
        this.parent.sortedChildren.forEachIndexed { i, child ->
            if (child == this) return i
        }
        throw IllegalArgumentException("Page is not in its parent's children list")
    }