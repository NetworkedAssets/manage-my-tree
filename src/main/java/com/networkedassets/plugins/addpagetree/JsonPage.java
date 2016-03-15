package com.networkedassets.plugins.addpagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@SuppressWarnings("WeakerAccess") // has to be public for jackson to be happy
@JsonIgnoreProperties(ignoreUnknown = true)
class JsonPage {
    public String id;
    public String text;
    public List<JsonPage> children;
    @JsonProperty("a_attr")
    public Attr attr;
    public String icon = "icon-page";

    public static JsonPage from(Page page) {
        JsonPage jpage = new JsonPage();
        jpage.id = Long.toString(page.getId());
        jpage.text = page.getDisplayTitle();
        jpage.children = page.getSortedChildren().stream().map(JsonPage::from).collect(Collectors.toList());
        jpage.attr = new Attr();
        return jpage;
    }

    Page modifyRootPageAndChildren(PageManager pageManager) {
        Page page = Objects.requireNonNull(pageManager.getPage(Long.parseLong(id)));
        renamePageIfNecessary(page, pageManager);

        createOrUpdateChildren(pageManager, page);

        return page;
    }

    private void createOrUpdateChildren(PageManager pageManager, Page page) {
        List<Long> childrenIds = new ArrayList<>();

        for (final JsonPage child : children) {
            long id = child.createOrUpdate(page, pageManager);
            childrenIds.add(id);
        }

        setChildrenOrder(page, childrenIds, pageManager);
    }

    private long createOrUpdate(Page parent, PageManager pageManager) {
        Page page;
        if (attr.isAdded) {
            page = new Page();
            page.setTitle(text);
            page.setSpace(parent.getSpace());
            page.setParentPage(parent);
            parent.addChild(page);
            page.setVersion(1);
            pageManager.saveContentEntity(page, null);
        } else {
            page = Objects.requireNonNull(pageManager.getPage(Long.parseLong(id)));
            renamePageIfNecessary(page, pageManager);
            movePageIfNecessary(page, parent, pageManager);
        }

        createOrUpdateChildren(pageManager, page);

        return page.getId();
    }

    private void setChildrenOrder(Page page, List<Long> childrenIds, PageManager pageManager) {
        pageManager.setChildPageOrder(page, childrenIds);
    }

    private void movePageIfNecessary(Page page, Page parent, PageManager pageManager) {
        if (!Objects.equals(page.getParent(), parent)) {
            pageManager.movePageAsChild(page, parent);
        }
    }

    private void renamePageIfNecessary(Page page, PageManager pageManager) {
        if (!Objects.equals(page.getTitle(), text)) {
            pageManager.renamePage(page, text);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Attr {
        @JsonProperty("data_added")
        public boolean isAdded = false;
    }
}
