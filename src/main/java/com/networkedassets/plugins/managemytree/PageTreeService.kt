package com.networkedassets.plugins.managemytree

import com.atlassian.confluence.pages.Page
import com.atlassian.confluence.pages.PageManager
import com.atlassian.confluence.security.Permission
import com.atlassian.confluence.security.PermissionManager
import com.atlassian.confluence.spaces.Space
import com.atlassian.confluence.spaces.SpaceManager
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory
import com.google.common.collect.Lists
import org.codehaus.jackson.annotate.JsonCreator
import org.codehaus.jackson.annotate.JsonProperty
import org.codehaus.jackson.map.ObjectMapper
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.Response

private val LAST_COMMANDS = "com.networkedassets.plugins.add-pagetree.LAST_COMMANDS"
private val OBJECT_MAPPER = ObjectMapper()
private val log = LoggerFactory.getLogger(PageTreeService::class.java)

@Path("/")
class PageTreeService(
        private val pageManager: PageManager,
        private val permissionManager: PermissionManager,
        private val spaceManager: SpaceManager,
        private val pluginSettingsFactory: PluginSettingsFactory
) {

    @POST
    @Path("manage")
    @Produces("application/json")
    @Consumes("application/json")
    fun managePages(@QueryParam("space") space: String, managePagesCommand: List<Command>?): Response {
        val s = spaceManager.getSpace(space)

        if (isUnauthorized(s)) return error("Unauthorized")
        if (managePagesCommand == null) return error("Did not get the modification commands")

        try {
            val error = executeCommands(s, managePagesCommand)
            if (error != null) return error
        } catch (e: IOException) {
            return error(e)
        }

        return success("Success")
    }

    private fun executeCommands(s: Space, commands: List<Command>): Response? {
        val ec = ExecutionContext(permissionManager, /*spaceBlueprintManager, blueprintContentGenerator,*/ s)
        val executedCommands = ArrayList<Command>(commands.size)
        for (command in commands) {
            try {
                command(pageManager, ec)
            } catch (e: Exception) {
                revertCommands(executedCommands, s)
                return error(e)
            }

            executedCommands.add(command)
        }
        persistLastChanges(executedCommands, ec)
        return null
    }

    @POST
    @Path("revertLast")
    @Produces("application/json")
    @Consumes("application/json")
    fun revertLastChange(@QueryParam("space") spaceKey: String): Response {
        try {
            val s = spaceManager.getSpace(spaceKey)
            val lastChanges = getLastChanges(s) ?: return error("No last changes")
            if (AuthenticatedUserThreadLocal.get().key.stringValue != lastChanges.userKey)
                return error("Unauthorized")
            val lastCommandList = lastChanges.executedCommands
            revertCommands(lastCommandList, s)

            return success("Success")
        } catch (e: IOException) {
            return error(e)
        }
    }

    private fun persistLastChanges(executedCommands: List<Command>, ec: ExecutionContext) {
        val pluginSettings = pluginSettingsFactory.createSettingsForKey(ec.space.key)
        val lc = LastChanges(executedCommands, AuthenticatedUserThreadLocal.get().key.stringValue)
        val serializedCommands = OBJECT_MAPPER.writeValueAsString(lc)
        pluginSettings.put(LAST_COMMANDS, serializedCommands)
    }

    private fun getLastChanges(space: Space): LastChanges? {
        val pluginSettings = pluginSettingsFactory.createSettingsForKey(space.key)
        val serializedCommands = pluginSettings.get(LAST_COMMANDS) as? String ?: return null
        return OBJECT_MAPPER.readValue(serializedCommands, LastChanges::class.java)
    }

    private fun revertCommands(executedCommands: List<Command>, s: Space) {
        for (executedCommand in Lists.reverse(executedCommands)) {
            executedCommand.revert(pageManager)
        }
        persistLastChanges(ArrayList<Command>(), ExecutionContext(permissionManager, /*spaceBlueprintManager, blueprintContentGenerator,*/ s))
    }

    @GET
    @Path("pagetree")
    @Produces("application/json")
    fun getPageTree(@QueryParam("space") spaceKey: String, @QueryParam("rootPageId") rootId: Long?): Response {
//        println("YO!!!!!")
        try {
            val space = spaceManager.getSpace(spaceKey)
            if (isUnauthorized(space)) return error("Unauthorized")

            val page = pageFromSpaceOrHomepage(rootId, space)
            val canCreate = permissionManager.hasCreatePermission(AuthenticatedUserThreadLocal.get(), space, Page::class.java)

            val lastChanges: LastChanges?
            var lastCommands: List<Command> = ArrayList()
            lastChanges = getLastChanges(space)
            if (lastChanges != null && AuthenticatedUserThreadLocal.get().key.stringValue == lastChanges.userKey)
                lastCommands = lastChanges.executedCommands

            val pageTreeInfo = PageTreeInfo(
                    canCreate,
                    JsonPage.from(
                            page,
                            AuthenticatedUserThreadLocal.get(),
                            permissionManager),
                    lastCommands)

            return Response.ok(OBJECT_MAPPER.writeValueAsString(pageTreeInfo)).build()
        } catch (e: Exception) {
            log.error("Exception: ", e)
            return error(e)
        }
    }

    private fun pageFromSpaceOrHomepage(pageId: Long?, space: Space): Page {
        val page = pageManager.getPage(pageId ?: return space.homePage)
        if (page == null || page.space != space) return space.homePage
        return page
    }

    private fun isUnauthorized(space: Space): Boolean {
        return !permissionManager.hasPermission(AuthenticatedUserThreadLocal.get(), Permission.VIEW, space)
    }
}

data class LastChanges
@JsonCreator constructor(
        @field:JsonProperty @param:JsonProperty("executedCommands") var executedCommands: List<Command>,
        @field:JsonProperty @param:JsonProperty("userKey") var userKey: String
)

data class PageTreeInfo
@JsonCreator constructor(
        @field:JsonProperty @param:JsonProperty("canCreate") var canCreate: Boolean,
        @field:JsonProperty @param:JsonProperty("pageTree") var pageTree: JsonPage,
        @field:JsonProperty @param:JsonProperty("lastChanges") var lastChanges: List<Command>
)

data class JsonMessage
@JsonCreator constructor(
        @field:JsonProperty @param:JsonProperty("status") var status: Int,
        @field:JsonProperty @param:JsonProperty("message") var message: String
)

fun error(message: String) = Response.status(500).entity(JsonMessage(500, message)).build()
fun error(exception: Exception) = error(exception.message ?: "")
fun success(message: String) = Response.ok(JsonMessage(200, message)).build()
