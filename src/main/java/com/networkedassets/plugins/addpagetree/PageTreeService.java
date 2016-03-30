package com.networkedassets.plugins.addpagetree;

import com.atlassian.confluence.pages.Page;
import com.atlassian.confluence.pages.PageManager;
import com.atlassian.confluence.security.Permission;
import com.atlassian.confluence.security.PermissionManager;
import com.atlassian.confluence.spaces.Space;
import com.atlassian.confluence.spaces.SpaceManager;
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.google.common.collect.Lists;
import com.networkedassets.plugins.addpagetree.managepages.Command;
import com.networkedassets.plugins.addpagetree.managepages.ExecutionContext;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.type.TypeFactory;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Path("/")
public class PageTreeService {
    private static final String LAST_COMMANDS = "com.networkedassets.plugins.add-pagetree.LAST_COMMANDS";
    private static final String LAST_ID_MAPPING = "com.networkedassets.plugins.add-pagetree.LAST_ID_MAPPING";
    @SuppressWarnings("unused")
    private static Logger log = LoggerFactory.getLogger(PageTreeService.class);
    private PageManager pageManager;
    private PermissionManager permissionManager;
    private SpaceManager spaceManager;
    private PluginSettingsFactory pluginSettingsFactory;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public PageTreeService(PageManager pageManager, PermissionManager permissionManager, SpaceManager spaceManager,
                           PluginSettingsFactory pluginSettingsFactory) {
        this.pageManager = pageManager;
        this.permissionManager = permissionManager;
        this.spaceManager = spaceManager;
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @POST
    @Path("manage")
    @Produces({"application/json"})
    @Consumes({"application/json"})
    public Response managePages(@QueryParam("space") String space, List<Command> managePagesCommand) {
        Space s = spaceManager.getSpace(space);

        if (isUnauthorized(s)) return error("Unauthorized");
        if (managePagesCommand == null) return error("Did not get the modification commands");

        try {
            final Response error = executeCommands(s, managePagesCommand);
            if (error != null) return error;
        } catch (final IOException e) {
            return error(e);
        }

        return success("Success");
    }

    @Nullable
    private Response executeCommands(Space s, List<Command> commands) throws IOException {
        final ExecutionContext ec = new ExecutionContext(permissionManager, s);
        final List<Command> executedCommands = new ArrayList<>(commands.size());
        for (Command command : commands) {
            try {
                command.execute(pageManager, ec);
            } catch (final Exception e) {
                revertCommands(ec, executedCommands);
                return error(e);
            }
            executedCommands.add(command);
        }
        persistLastChanges(executedCommands, ec);
        return null;
    }

    @POST
    @Path("revertLast")
    @Produces({"application/json"})
    @Consumes({"application/json"})
    public Response revertLastChange(@QueryParam("space") String spaceKey) {
        try {
            Space s = spaceManager.getSpace(spaceKey);
            List<Command> lastCommandList = getLastCommandList(s);
            ExecutionContext lastExecutionContext = getLastExecutionContext(s);
            revertCommands(lastExecutionContext, lastCommandList);
        } catch (IOException e) {
            return error(e);
        }
        return success("Success");
    }

    private void persistLastChanges(List<Command> executedCommands, ExecutionContext ec) throws IOException {
        PluginSettings pluginSettings = pluginSettingsFactory.createSettingsForKey(ec.getSpace().getKey());
        String serializedCommands = OBJECT_MAPPER.writeValueAsString(executedCommands);
        pluginSettings.put(LAST_COMMANDS, serializedCommands);
        String serializedIdMapping = OBJECT_MAPPER.writeValueAsString(ec.getIdMapping());
        pluginSettings.put(LAST_ID_MAPPING, serializedIdMapping);
    }

    private List<Command> getLastCommandList(Space space) throws IOException {
        PluginSettings pluginSettings = pluginSettingsFactory.createSettingsForKey(space.getKey());
        String serializedCommands = (String) pluginSettings.get(LAST_COMMANDS);
        return OBJECT_MAPPER.readValue(serializedCommands,
                TypeFactory.defaultInstance().constructCollectionType(List.class, Command.class));
    }

    private ExecutionContext getLastExecutionContext(Space space) throws IOException {
        PluginSettings pluginSettings = pluginSettingsFactory.createSettingsForKey(space.getKey());
        String serializedIdMapping = (String) pluginSettings.get(LAST_ID_MAPPING);
        Map<String, Long> idMapping = OBJECT_MAPPER.readValue(serializedIdMapping,
                TypeFactory.defaultInstance().constructMapType(Map.class, String.class, Long.class));
        return new ExecutionContext(permissionManager, space, idMapping);
    }

    private void revertCommands(ExecutionContext ec, List<Command> executedCommands) {
        for (Command executedCommand : Lists.reverse(executedCommands)) {
            executedCommand.revert(pageManager, ec);
        }
    }

    @GET
    @Path("pagetree")
    @Produces({"application/json"})
    public Response getPageTree(@QueryParam("space") String spaceKey, @QueryParam("rootPageId") Long rootId) {
        Space space = spaceManager.getSpace(spaceKey);
        if (isUnauthorized(space)) return error("Unauthorized");

        Page page = pageFromSpaceOrHomepage(rootId, space);

        final boolean canCreate = permissionManager.hasCreatePermission(AuthenticatedUserThreadLocal.get(), space, Page.class);
        PageTreeInfo pageTreeInfo = new PageTreeInfo(canCreate, JsonPage.from(page,
                AuthenticatedUserThreadLocal.get(),
                permissionManager));

        return Response.ok(pageTreeInfo).build();
    }

    @SuppressWarnings("WeakerAccess") // has to be public for jackson to be happy
    public static class PageTreeInfo {
        public boolean canCreate;
        public JsonPage pageTree;

        public PageTreeInfo(boolean canCreate, JsonPage pageTree) {
            this.canCreate = canCreate;
            this.pageTree = pageTree;
        }
    }

    private Page pageFromSpaceOrHomepage(Long pageId, Space space) {
        Page page;
        if (pageId == null || (page = pageManager.getPage(pageId)) == null || page.getSpace() != space)
            return space.getHomePage();
        else return page;
    }

    private boolean isUnauthorized(Space space) {
        return permissionManager == null || !permissionManager
                .hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, space);
    }

    private static Response error(String message) {
        return Response.status(500).entity(new JsonMessage(500, message)).build();
    }

    private static Response error(Exception exception) {
        return error(exception.getLocalizedMessage());
    }

    private static Response success(String message) {
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
