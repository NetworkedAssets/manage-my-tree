package com.networkedassets.plugins.managemytree

import com.atlassian.activeobjects.external.ActiveObjects
import com.networkedassets.plugins.managemytree.opml.Opml
import com.networkedassets.plugins.managemytree.opml.Outline
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

@Path("/templates")
@Produces("application/json")
@Consumes("application/json")
class TemplateService {
    val customTemplateManager by lazy { CustomTemplateManager.instance }

    @Context
    lateinit var uriInfo: UriInfo

    @POST
    fun createTemplate(template: CustomTemplate): Response {
        val createdTemplate = customTemplateManager.create(template)

        return Response
                .created(uriInfo.absolutePathBuilder.path(createdTemplate.id.toString()).build())
                .entity(createdTemplate.asJson())
                .build()
    }

    @POST
    @Consumes("application/xml")
    fun createTemplate(opml: Opml): Response {
        val template = opml.toCustomTemplate();
        return createTemplate(template)
    }

    @GET @Path("{id}")
    fun getTemplate(@PathParam("id") id: Int): Response =
            Response.ok().entity(customTemplateManager.getById(id).asJson()).build()

    @DELETE @Path("{id}")
    fun deleteTemplate(@PathParam("id") id: Int): Response {
        customTemplateManager.remove(id);
        //language=JSON
        return Response.ok("""{"status": "ok"}""").build()
    }

    @GET
    fun getAll(@DefaultValue("true") @QueryParam("withBody") withBody: Boolean): Response {
        @Suppress("unused")
        val json = if (withBody) {
            customTemplateManager.getAll().asJson()
        } else {
            customTemplateManager.getAll().map {
                object {
                    val name = it.name;
                    val id = it.id
                }
            }.asJson()
        }
        return Response.ok().entity(json).build()
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

class CustomTemplateManager(val ao: ActiveObjects) {
    fun getAll() = ao.find(CustomTemplateAO::class.java).map { it.toCustomTemplate() }
    fun getById(id: Int) = ao.get(CustomTemplateAO::class.java, id).toCustomTemplate()

    fun create(template: CustomTemplate): CustomTemplate = ao.executeInTransaction {
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

        templateEntity.toCustomTemplate()//.let { println(it); it }
    }

    private fun createChildren(parent: CustomOutline, parentEntity: CustomOutlineAO) {
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
        ao.delete(*ao.get(CustomTemplateAO::class.java, id).thisAndAllOutlines)
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
            entities += o;
            o.children.forEach(::addSelfAndDescendants)
        }

        entities += this
        outlines.forEach(::addSelfAndDescendants)

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
data class CustomTemplate
@JsonCreator constructor(
        @param:JsonProperty("name") @get:JsonProperty("name") val name: String,
        @param:JsonProperty("outlines") @get:JsonProperty("outlines") val outlines: List<CustomOutline>,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: TemplateId.Custom?
) {
    constructor(name: String, outlines: List<CustomOutline>) : this(name, outlines, null)
}

//region toCustomTemplate conversions
fun CustomTemplateAO.toCustomTemplate() = CustomTemplate(
        name = this.name,
        outlines = this.outlines.map { it.toCustomOutline() },
        id = TemplateId.Custom(this.id)
)

fun Opml.toCustomTemplate() = CustomTemplate(
        name = this.head.title ?: "template without title",
        outlines = this.body.outline.map { it.toCustomOutline() },
        id = null
)
//endregion

data class CustomOutline
@JsonCreator constructor(
        @param:JsonProperty("title") @get:JsonProperty("title") val title: String,
        @param:JsonProperty("text") @get:JsonProperty("text") val text: String,
        @param:JsonProperty("children") @get:JsonProperty("children") val children: List<CustomOutline>,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: Int?
) {
    constructor(title: String, text: String, children: List<CustomOutline>) : this(title, text, children, null)
}

//region toCustomOutline conversions
fun CustomOutlineAO.toCustomOutline(): CustomOutline = CustomOutline(
        title = this.title,
        text = this.text,
        children = this.children.map { it.toCustomOutline() },
        id = this.id
)

fun Outline.toCustomOutline(): CustomOutline = CustomOutline(
        title = this.title,
        text = this.text ?: "",
        children = this.outline.map { it.toCustomOutline() },
        id = null
)
//endregion
//endregion
