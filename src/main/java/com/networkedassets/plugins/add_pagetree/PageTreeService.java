package com.networkedassets.plugins.add_pagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.fugue.Either;
import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.annotate.JsonProperty;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Path("/")
public class PageTreeService {
    @SuppressWarnings("unused")
    private static org.slf4j.Logger log = LoggerFactory.getLogger(PageTreeService.class);
    private PageManager pageManager;
    private PermissionManager permissionManager;
    private SpaceManager spaceManager;


    public PageTreeService(PageManager pageManager, PermissionManager permissionManager, SpaceManager spaceManager) {
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.spaceManager = spaceManager;
    }

    @SuppressWarnings("LoopStatementThatDoesntLoop")
    @POST
    @Path("manage")
    @Produces({"application/json"})
    @Consumes({"application/json"})
    public Response addPages(@QueryParam("space") String space, ManagePagesCommand managePagesCommand) {
        if (isUnauthorized(space)) return error("Unauthorized");

        final Either<ManagePagesCommand, Response> commandOrError = validateCommand(managePagesCommand);

        for (Response error : commandOrError.right()) {
            return error;
        }

        try {
            for (ManagePagesCommand command : commandOrError.left()) {
                command.execute(pageManager);
            }
        } catch (final Exception e) {
            return error(e);
        }

        return success("success");
    }

    private Either<ManagePagesCommand, Response> validateCommand(ManagePagesCommand command) {
        if (command == null || command.root == null || command.forDeletion == null) {
            return Either.right(error("Did not get the pagetree"));
        }
        return Either.left(command);
    }

    @GET
    @Path("pagetree")
    @Produces({"application/json"})
    public Response getPageTree(@QueryParam("space") String spaceKey, @QueryParam("rootPageId") Long rootId) {
        Space space = spaceManager.getSpace(spaceKey);
        if (isUnauthorized(space)) return error("Unauthorized");

        Page page = pageFromSpaceOrHomepage(rootId, space);

        return Response.ok(JsonPage.from(page)).build();
    }

    private Page pageFromSpaceOrHomepage(Long pageId, Space space) {
        Page page;
        if (pageId == null || (page = pageManager.getPage(pageId)) == null || page.getSpace() != space)
            return space.getHomePage();
        else return page;
    }

    private boolean isUnauthorized(Space space) {
        return permissionManager == null || !permissionManager
                .hasCreatePermission(AuthenticatedUserThreadLocal.get(), space, Page.class);
    }

    private boolean isUnauthorized(String spaceKey) {
        return isUnauthorized(spaceManager.getSpace(spaceKey));
    }

    private Response error(String message) {
        return Response.status(500).entity(new JsonMessage(500, message)).build();
    }

    private Response error(Exception exception) {
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps;
        try {
            ps = new PrintStream(baos, true, "utf-8");
        } catch (UnsupportedEncodingException e) {
            // unreachable
            // ...
            throw new RuntimeException(e);
            // better safe than sorry
        }
        exception.printStackTrace(ps);
        return error(baos.toString());
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
        @JsonProperty("a_attr")
        public Attr attr;
        public String icon = "icon-page";

        public static JsonPage from(Page page) {
            JsonPage jpage = new JsonPage();
            jpage.id = Long.toString(page.getId());
            jpage.text = page.getDisplayTitle();
            jpage.children = page.getChildren().stream().map(JsonPage::from).collect(Collectors.toList());
            jpage.attr = new Attr();
            jpage.attr.isAdded = false;
            return jpage;
        }

        private Page addPageAndChildrenTo(PageManager pageManager) {
            final Page page = pageManager.getPage(Long.parseLong(id));
            renameIfNecessary(page);
            for (final JsonPage child : children) {
                child.setParent(page, pageManager);
            }
            return page;
        }

        private void renameIfNecessary(Page page) {
            if (!Objects.equals(page.getTitle(), text)) {
                page.setTitle(text);
            }
        }

        private void setParent(Page parent, PageManager pageManager) {
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
                renameIfNecessary(page);
                if (!Objects.equals(page.getParent(), parent))
                    pageManager.movePageAsChild(page, parent);
                else
                    pageManager.saveContentEntity(page, null); // movePageAsChild already saves
            }

            for (final JsonPage child : children) {
                child.setParent(page, pageManager);
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private static class Attr {
            @JsonProperty("data_added")
            public boolean isAdded = false;
        }
    }

    private static class ManagePagesCommand {
        public JsonPage root;
        public List<String> forDeletion;

        public void execute(PageManager pageManager) {
            root.addPageAndChildrenTo(pageManager);

            for (String sid : forDeletion) {
                final Page pageToDelete = pageManager.getPage(Long.parseLong(sid));
                deleteWithDescendants(pageToDelete, pageManager);
            }
        }

        private void deleteWithDescendants(Page page, PageManager pageManager) {
            new ArrayList<>(page.getChildren()).forEach(p -> this.deleteWithDescendants(p, pageManager));
            page.trash();
            pageManager.saveContentEntity(page, null);
        }
    }
}
