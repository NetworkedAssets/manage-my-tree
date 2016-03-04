package com.networkedassets.plugins.add_pagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.google.gson.Gson;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

@Path("/")
public class PageTreeService {
    private static Gson gson = new Gson();
    private static org.slf4j.Logger log = LoggerFactory.getLogger(PageTreeService.class);
    private PageManager pageManager;
    private PermissionManager permissionManager;
    private SpaceManager spaceManager;


    public PageTreeService(PageManager pageManager, PermissionManager permissionManager, SpaceManager spaceManager) {
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.spaceManager = spaceManager;
    }

    @POST
    @Path("manage")
    @Produces({"application/json"})
    @Consumes({"application/json"})
    public Response addPages(@QueryParam("space") String space, List<JsonPage> pages) throws Exception {
        log.warn("in addPages()");

        if (permissionManager != null && permissionManager
                .hasCreatePermission(AuthenticatedUserThreadLocal.get(), spaceManager.getSpace(space), Page.class)) {
            log.warn("username: " + AuthenticatedUserThreadLocal.getUsername());

            if (pageManager == null) {
                return error("pageManager not injected!");
            }

            log.debug("pages: ", gson.toJson(pages));

            if (pages == null) {
                return error("Did not get the pagetree");
            }

            if (pages.size() != 1) {
                return error("Invalid number of roots: " + pages.size());
            }

            try {
                pages.get(0).addPages(pageManager);
            } catch (final Exception e) {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                final PrintStream ps = new PrintStream(baos, true, "utf-8");
                e.printStackTrace(ps);
                return error(baos.toString());
            }

            return success("success");
        }

        return error("Unauthorized");
    }

    private Response error(String message) {
        return Response.status(500).entity(new JsonMessage(500, message)).build();
    }

    private Response success(String message) {
        return Response.ok(new JsonMessage(200, message)).build();
    }

    public static class JsonMessage {
        public int status;
        public String message;

        public JsonMessage(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonPage {
        public String id;
        public String text;
        public List<JsonPage> children;

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
