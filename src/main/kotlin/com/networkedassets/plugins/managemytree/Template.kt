package com.networkedassets.plugins.managemytree

import com.atlassian.activeobjects.external.ActiveObjects
import com.atlassian.confluence.plugins.createcontent.extensions.ContentTemplateModuleDescriptor
import com.atlassian.confluence.plugins.createcontent.extensions.SpaceBlueprintModuleDescriptor
import com.atlassian.confluence.plugins.createcontent.rest.BlueprintWebItemService
import com.atlassian.confluence.user.AuthenticatedUserThreadLocal
import com.atlassian.confluence.util.i18n.I18NBean
import com.atlassian.confluence.util.i18n.I18NBeanFactory
import com.atlassian.plugin.PluginAccessor
import com.networkedassets.plugins.managemytree.opml.Opml
import com.networkedassets.plugins.managemytree.opml.OpmlOutline
import net.java.ao.Entity
import net.java.ao.OneToMany
import net.java.ao.Preload
import net.java.ao.schema.NotNull
import org.codehaus.jackson.annotate.*
import org.codehaus.jackson.map.ObjectMapper
import java.util.*
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import kotlin.properties.Delegates.notNull

private val OBJECT_MAPPER = ObjectMapper()

fun Any.asJson() = OBJECT_MAPPER.writeValueAsString(this)

val IGNORED_MODULES: List<String> = listOf(
        "com.atlassian.confluence.plugins.confluence-space-blueprints:documentation-space-blueprint",
        "com.atlassian.confluence.plugins.confluence-knowledge-base:kb-blueprint",
        "com.atlassian.confluence.plugins.confluence-space-blueprints:team-space-blueprint"
)

//region json stuff
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "templateType")
@JsonSubTypes(
        JsonSubTypes.Type(TemplateId.FromBlueprint::class, name = "fromBlueprint"),
        JsonSubTypes.Type(TemplateId.Custom::class, name = "custom")
)
@JsonIgnoreProperties(ignoreUnknown = true)
//endregion
sealed class TemplateId {
    @JsonTypeName("fromBlueprint")
    class FromBlueprint
    @JsonCreator constructor(
            @param:JsonProperty("spaceBlueprintId") val spaceBlueprintId: String
    ) : TemplateId() {
        @JsonIgnore
        fun getFromBlueprint() =
                (TemplateService.instance.pluginManager
                        .getPluginModule(spaceBlueprintId) as SpaceBlueprintModuleDescriptor)
                        .toTemplate()

        //region equals, hashcode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as FromBlueprint

            if (spaceBlueprintId != other.spaceBlueprintId) return false

            return true
        }

        override fun hashCode(): Int {
            return spaceBlueprintId.hashCode()
        }

        override fun toString(): String {
            return "FromBlueprint(spaceBlueprintId='$spaceBlueprintId')"
        }
        //endregion
    }

    @JsonTypeName("custom")
    class Custom
    @JsonCreator constructor(
            @param:JsonProperty("customOutlineId") val customOutlineId: Int
    ) : TemplateId() {
        @JsonIgnore
        fun getFromDb() = CustomTemplateManager.instance.getById(customOutlineId)

        //region equals, hashcode, toString
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Custom

            if (customOutlineId != other.customOutlineId) return false

            return true
        }

        override fun hashCode(): Int {
            return customOutlineId
        }

        override fun toString(): String {
            return "Custom(customOutlineId=$customOutlineId)"
        }
        //endregion
    }
}

data class TemplatePartId
@JsonCreator constructor(
        @param:JsonProperty("partId") @get:JsonProperty("partId") val partId: String,
        @param:JsonProperty("templateId") @get:JsonProperty("templateId") val templateId: TemplateId) {
    @JsonIgnore
    fun getOutlineWithoutChildren() = if (templateId is TemplateId.FromBlueprint) {
        val pluginModule = TemplateService.instance.pluginManager.getPluginModule(
                partId
        ) as ContentTemplateModuleDescriptor

        val pageTemplate = pluginModule.module
        Outline(
                title = pageTemplate.title,
                text = pageTemplate.content,
                id = this,
                children = listOf()
        )
    } else {
        CustomTemplateManager.instance.getOutlineById(this)
    }
}

@Path("/templates")
@Produces("application/json")
@Consumes("application/json")
class TemplateService(
        private val customTemplateManager: CustomTemplateManager,
        val pluginManager: PluginAccessor,
        private val webItemService: BlueprintWebItemService,
        private val i18NBeanFactory: I18NBeanFactory
) {
    @Context
    lateinit var uriInfo: UriInfo

    @POST
    fun createCustomTemplate(template: Template): Response {
        val createdTemplate = customTemplateManager.create(template)

        return Response
                .created(uriInfo.absolutePathBuilder.path(createdTemplate.id.toString()).build())
                .entity(createdTemplate.asJson())
                .build()
    }

    @POST
    @Consumes("application/xml")
    fun createCustomTemplate(opml: Opml): Response {
        val template = opml.toTemplate()
        return createCustomTemplate(template)
    }

    @GET @Path("{id}")
    fun getCustomTemplate(@PathParam("id") id: Int): Response =
            Response.ok().entity(customTemplateManager.getById(id).asJson()).build()

    @GET @Path("blueprint/{id}")
    fun getBlueprintTemplate(@PathParam("id") moduleKey: String): Response {
        val sbmd = pluginManager.getPluginModule(moduleKey) as SpaceBlueprintModuleDescriptor
        return Response.ok().entity(sbmd.toTemplate()!!.asJson()).build()
    }

    @DELETE @Path("{id}")
    fun deleteCustomTemplate(@PathParam("id") id: Int): Response {
        customTemplateManager.remove(id)
        //language=JSON
        return Response.ok("""{"status": "ok"}""").build()
    }

    @GET
    fun getAll(@DefaultValue("true") @QueryParam("withBody") withBody: Boolean): Response {
        val customTemplates = customTemplateManager.getAll()
        val blueprintTemplates = getAllBlueprintTemplates(headerOnly = !withBody)
        val allTemplates = customTemplates + blueprintTemplates
        @Suppress("unused")
        val json = if (withBody) {
            allTemplates.asJson()
        } else {
            allTemplates.map {
                object {
                    val name = it.name
                    val id = it.id
                }
            }.asJson()
        }
        return Response.ok().entity(json).build()
    }

    fun getAllBlueprintTemplates(headerOnly: Boolean = false): List<Template> {
        return webItemService
                .getCreateSpaceWebItems(i18NBeanFactory.i18NBean, null, AuthenticatedUserThreadLocal.get())
                .asSequence()
                .map {
                    if (it.blueprintModuleCompleteKey in IGNORED_MODULES)
                        null
                    else
                        pluginManager.getPluginModule(it.blueprintModuleCompleteKey) as? SpaceBlueprintModuleDescriptor
                }
                .filterNotNull()
                .map {
                    it.toTemplate(headerOnly)
                }
                .filterNotNull()
                .toList()
    }

    //region singleton stuff
    init {
        _instance = this
    }

    @Suppress("unused")
    companion object {
        private var _instance: TemplateService by notNull()
        val instance: TemplateService
            get() = _instance
    }
    //endregion
}

class CustomTemplateManager(private val ao: ActiveObjects) {
    fun getAll() = ao.find(CustomTemplateAO::class.java).map { it.toTemplate() }
    fun getById(id: Int) = ao.get(CustomTemplateAO::class.java, id).toTemplate()

    fun getOutlineById(id: TemplatePartId) = ao.get(CustomOutlineAO::class.java, id.partId.toInt())
            .toOutline(id.templateId)

    fun create(template: Template): Template = ao.executeInTransaction {
        val templateEntity = ao.create(CustomTemplateAO::class.java,
                mapOf("NAME" to template.name))
        templateEntity.save()

        for (outline in template.outlines) {
            val rootEntity = ao.create(CustomOutlineAO::class.java, mapOf(
                    "TITLE" to outline.title,
                    "TEXT" to outline.text,
                    "TEMPLATE_ID" to templateEntity.id))
            rootEntity.save()
            createChildren(outline, rootEntity)
        }

        templateEntity.toTemplate()//.let { println(it); it }
    }

    private fun createChildren(parent: Outline, parentEntity: CustomOutlineAO) {
        parent.children.forEach { child ->
            val childEntity = ao.create(CustomOutlineAO::class.java, mapOf(
                    "TITLE" to child.title,
                    "TEXT" to child.text,
                    "PARENT_ID" to parentEntity.id))
            childEntity.save()
            createChildren(child, childEntity)
        }
    }

    fun remove(id: Int) = ao.executeInTransaction {
        ao.get(CustomTemplateAO::class.java, id).thisAndAllOutlines.forEach { ao.delete(it) }
    }

    //region singleton stuff
    init {
        _instance = this
    }

    companion object {
        private var _instance: CustomTemplateManager by notNull()
        val instance: CustomTemplateManager
            get() = _instance

    }
    //endregion
}

//region entities
@Preload
interface CustomTemplateAO : Entity {
    @get:NotNull
    var name: String

    @get:OneToMany(reverse = "getTemplate")
    val outlines: Array<CustomOutlineAO>
}

val CustomTemplateAO.thisAndAllOutlines: Array<Entity>
    get() {
        val entities = ArrayList<Entity>(127)
        fun addSelfAndDescendants(o: CustomOutlineAO) {
            o.children.forEach(::addSelfAndDescendants)
            entities += o
        }

        outlines.forEach(::addSelfAndDescendants)
        entities += this

        return entities.toTypedArray()
    }

@Preload
interface CustomOutlineAO : Entity {
    @get:NotNull
    var title: String
    @get:NotNull
    var text: String
    var parent: CustomOutlineAO?
    @Suppress("unused")
    var template: CustomTemplateAO?

    @get:OneToMany(reverse = "getParent")
    val children: Array<CustomOutlineAO>
}
//endregion

//region data
data class Template
@JsonCreator constructor(
        @param:JsonProperty("name") @get:JsonProperty("name") val name: String,
        @param:JsonProperty("outlines") @get:JsonProperty("outlines") val outlines: List<Outline>,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: TemplateId?
) {
    constructor(name: String, outlines: List<Outline>) : this(name, outlines, null)
}

//region toCustomTemplate conversions
fun CustomTemplateAO.toTemplate(): Template {
    val id = TemplateId.Custom(this.id)
    return Template(
            name = this.name,
            outlines = this.outlines.map { it.toOutline(id) },
            id = id
    )
}

fun Opml.toTemplate() = Template(
        name = this.head.title ?: "template without title",
        outlines = this.body.outline.map { it.toOutline() },
        id = null
)

fun SpaceBlueprintModuleDescriptor.toTemplate(headerOnly: Boolean = false): Template? {
    if (this.contentTemplateRefNode == null) return null
    val id = TemplateId.FromBlueprint(this.completeKey)
    return Template(
            name = tryGetI18n(this)?.getText(this.i18nNameKey) ?: this.name ?: "blankSpace",
            outlines = if (headerOnly) listOf() else listOf(this.contentTemplateRefNode.toOutline(id)),
            id = id
    )
}

fun tryGetI18n(spaceBlueprintModuleDescriptor: SpaceBlueprintModuleDescriptor): I18NBean? {
    val contentModuleDesc = TemplateService.instance.pluginManager
            .getPluginModule(spaceBlueprintModuleDescriptor.contentTemplateRefNode.ref.completeKey) as ContentTemplateModuleDescriptor
    return contentModuleDesc.javaClass.declaredFields.filter { it.type == I18NBeanFactory::class.java }.firstOrNull()?.let {
        it.isAccessible = true
        val i18nFact = it.get(contentModuleDesc) as I18NBeanFactory
        i18nFact.i18NBean
    }
}
//endregion

data class Outline
@JsonCreator constructor(
        @param:JsonProperty("title") @get:JsonProperty("title") val title: String,
        @param:JsonProperty("text") @get:JsonProperty("text") val text: String,
        @param:JsonProperty("children") @get:JsonProperty("children") val children: List<Outline>,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: TemplatePartId?
) {
    constructor(title: String, text: String, children: List<Outline>) : this(title, text, children, null)
}

//region toCustomOutline conversions
fun CustomOutlineAO.toOutline(templateId: TemplateId): Outline = Outline(
        title = this.title,
        text = this.text,
        children = this.children.map { it.toOutline(templateId) },
        id = TemplatePartId(this.id.toString(), templateId)
)

fun OpmlOutline.toOutline(): Outline = Outline(
        title = this.title,
        text = this.text ?: "",
        children = this.outline.map { it.toOutline() }
)

fun SpaceBlueprintModuleDescriptor.ContentTemplateRefNode.toOutline(templateId: TemplateId): Outline {
    val pluginModule = TemplateService.instance.pluginManager.getPluginModule(
            this.ref.completeKey
    ) as ContentTemplateModuleDescriptor

    val pageTemplate = pluginModule.module
    return Outline(
            title = pageTemplate.title,
            text = pageTemplate.content,
            children = this.children.map { it.toOutline(templateId) },
            id = TemplatePartId(pluginModule.completeKey, templateId)
    )
}
//endregion
//endregion
