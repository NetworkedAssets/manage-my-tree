package com.networkedassets.plugins.addpagetree;

import com.atlassian.confluence.pages.Page;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;

import java.util.List;
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class Attr {
        @JsonProperty("data_added")
        public boolean isAdded = false;
    }
}
