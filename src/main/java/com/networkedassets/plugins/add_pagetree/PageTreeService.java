package com.networkedassets.plugins.add_pagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.fugue.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

@Path("/")
public class PageTreeService {
    @SuppressWarnings("unused")
    private static Logger log = LoggerFactory.getLogger(PageTreeService.class);
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
    public Response managePages(@QueryParam("space") String space, ManagePagesCommand managePagesCommand) {
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
            return Either.right(error("Did not get the page tree"));
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
                .hasPermission(AuthenticatedUserThreadLocal.get(), Permission.ADMINISTER, space);
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

    @SuppressWarnings("WeakerAccess") // has to be public for jackson to be happy
    public static class JsonMessage {
        public int status;
        public String message;

        public JsonMessage(int status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
