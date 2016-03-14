package com.networkedassets.plugins.add_pagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("WeakerAccess") // has to be public for jackson to be happy
class ManagePagesCommand {
    public JsonPage root;
    public List<String> forDeletion;

    public void execute(PageManager pageManager) {
        root.modifyRootPageAndChildren(pageManager);

        for (String sid : forDeletion) {
            final Page pageToDelete = pageManager.getPage(Long.parseLong(sid));
            deleteWithDescendants(pageToDelete, pageManager);
        }
    }

    private void deleteWithDescendants(Page page, PageManager pageManager) {
        new ArrayList<>(page.getChildren()).forEach(p -> this.deleteWithDescendants(p, pageManager));
        pageManager.trashPage(page);
    }
}
