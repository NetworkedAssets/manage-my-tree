package com.networkedassets.plugins.add_pagetree;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

import org.slf4j.LoggerFactory;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.spaces.actions.AbstractSpaceAction;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

@SuppressWarnings("serial")
public class CreatePageTreeAction extends AbstractSpaceAction {

    private static Gson gson = new Gson();
    private static org.slf4j.Logger log = LoggerFactory.getLogger(CreatePageTreeAction.class);
    private PageManager pageManager;
    private String pageTreeString;
    private String message;

    public String getMessage() {
        return message;
    }

    public void setPageTreeString(String pageTreeString) {
        this.pageTreeString = pageTreeString;
    }

    public void setPageManager(PageManager pageManager) {
        this.pageManager = pageManager;
    }

    public String addPages() throws Exception {

        if (pageManager == null) {
            message = "pageManager not injected!";
            return ERROR;
        }

        final List<JsonPage> pages = gson.fromJson(pageTreeString, new TypeToken<List<JsonPage>>() {}.getType());
        log.debug("pages: ", gson.toJson(pages));
        
        if (pages == null) {
            message = "Did not get the pagetree";
            return ERROR;
        }

        if (pages.size() != 1) {
            message = "Invalid number of roots: " + pages.size();
            return ERROR;
        }

        try {
            pages.get(0).addPages(pageManager);
        } catch (final Exception e) {
            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final PrintStream ps = new PrintStream(baos, true, "utf-8");
            e.printStackTrace(ps);
            message = baos.toString();
            return ERROR;
        }

        return SUCCESS;
    }

    private static class JsonPage {
        private String id;
        private String text;
        private List<JsonPage> children;

        private Page addPages(PageManager pageManager) {
            final Page page = pageManager.getPage(Long.parseLong(id));
            for (final JsonPage child : children) {
                child.setParent(page, pageManager);
            }
            return page;
        }

        private void setParent(Page parent, PageManager pageManager) {
            final Page page = new Page();
            page.setTitle(text);
            page.setSpace(parent.getSpace());
            page.setParentPage(parent);
            parent.addChild(page);
            page.setVersion(1);
            pageManager.saveContentEntity(page, null);
            for (final JsonPage child : children) {
                child.setParent(page, pageManager);
            }
        }
    }
}
