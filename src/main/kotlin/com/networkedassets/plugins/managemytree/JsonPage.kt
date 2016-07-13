package com.networkedassets.plugins.managemytree

import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.user.ConfluenceUser
import org.codehaus.jackson.annotate.JsonIgnoreProperties

@Suppress("unused")
@JsonIgnoreProperties(ignoreUnknown = true)
class JsonPage(
        val id: String,
        val text: String,
        val children: List<JsonPage>,
        val type: String = "default",
        val a_attr: Attr
) {
    data class Attr(
            val data_canEdit: Boolean = false,
            val data_canRemove: Boolean = false
    )

    companion object {
        fun from(page: Page, user: ConfluenceUser, permissionManager: PermissionManager, isRoot: Boolean = false): JsonPage = JsonPage(
                id = page.id.toString(),
                text = page.displayTitle,
                a_attr = Attr(
                        permissionManager.hasPermission(user, Permission.EDIT, page),
                        if (isRoot) false else permissionManager.hasPermission(user, Permission.REMOVE, page)
                ),
                children = page.sortedChildren
                        .filter({ p -> permissionManager.hasPermission(user, Permission.VIEW, p) })
                        .map({ p -> JsonPage.from(p, user, permissionManager) })
        )
    }
}
