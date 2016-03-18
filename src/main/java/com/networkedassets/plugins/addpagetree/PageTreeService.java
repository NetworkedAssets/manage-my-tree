package com.networkedassets.plugins.addpagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.fugue.Either;
import com.networkedassets.plugins.addpagetree.managepages.Command;
import com.networkedassets.plugins.addpagetree.managepages.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.util.List;

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
    public Response managePages(@QueryParam("space") String space, List<Command> managePagesCommand) {
        Space s = spaceManager.getSpace(space);

        if (isUnauthorized(s)) return error("Unauthorized");

        final Either<List<Command>, Response> commandOrError = validateCommands(managePagesCommand);

        for (Response error : commandOrError.right()) {
            return error;
        }

        try {
            ExecutionContext ec = new ExecutionContext(permissionManager, s);
            for (List<Command> commands : commandOrError.left()) {
                for (Command command : commands) {
                    command.execute(pageManager, ec);
                }
            }
        } catch (final Exception e) {
            return error(e);
        }

        return success("success");
    }

    private Either<List<Command>, Response> validateCommands(List<Command> commands) {
        if (commands == null || commands.isEmpty()) {
            return Either.right(error("Did not get the modification commands"));
        }
        return Either.left(commands);
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

    private Response error(String message) {
        return Response.status(500).entity(new JsonMessage(500, message)).build();
    }

    private Response error(Exception exception) {
        return error(exception.getLocalizedMessage());
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
