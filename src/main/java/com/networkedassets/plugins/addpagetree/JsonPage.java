package com.networkedassets.plugins.addpagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.confluence.user.ConfluenceUser;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;

import java.util.List;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess") // has to be public for jackson to be happy
@JsonIgnoreProperties(ignoreUnknown = true)
class JsonPage {
    public String id;
    public String text;
    public List<JsonPage> children;
    public String type = "default";
    public Attr a_attr;

    public static JsonPage from(Page page, ConfluenceUser user, PermissionManager permissionManager) {
        JsonPage jpage = new JsonPage();
        jpage.id = Long.toString(page.getId());
        jpage.text = page.getDisplayTitle();
        jpage.a_attr = new Attr();
        jpage.a_attr.data_canEdit = permissionManager.hasPermission(user, Permission.EDIT, page);
        jpage.a_attr.data_canRemove = permissionManager.hasPermission(user, Permission.REMOVE, page);

        jpage.children = page.getSortedChildren().stream()
                .filter(p -> permissionManager.hasPermission(user, Permission.VIEW, p))
                .map(p -> JsonPage.from(p, AuthenticatedUserThreadLocal.get(), permissionManager))
                .collect(Collectors.toList());
        return jpage;
    }

    static class Attr {
        public boolean data_canEdit;
        public boolean data_canRemove;
    }
}
