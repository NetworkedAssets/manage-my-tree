package com.networkedassets.plugins.managemytree

import com.atlassian.activeobjects.external.ActiveObjects
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

    @GET @Path("{id}")
    fun getTemplate(@PathParam("id") id: Int): Response =
            Response.ok().entity(customTemplateManager.getById(id).asJson()).build()

    @DELETE @Path("{id}")
    fun deleteTemplate(@PathParam("id") id: Int): Response {
        customTemplateManager.remove(id);
        return Response.ok().build()
    }

    @GET
    fun getAll(): Response =
            Response.ok().entity(customTemplateManager.getAll().asJson()).build()

    init {
        _instance = this
    }

    companion object {
        private var _instance: TemplateService by notNull()
        val instance: TemplateService
            get() = _instance
    }
}

class CustomTemplateManager(val ao: ActiveObjects) {
    fun getAll() = ao.find(CustomTemplateAO::class.java).map { it.toCustomTemplate() }
    fun getById(id: Int) = ao.get(CustomTemplateAO::class.java, id).toCustomTemplate()

    fun create(template: CustomTemplate): CustomTemplate = ao.executeInTransaction {
        val rootEntity = ao.create(CustomOutlineAO::class.java, mapOf("TITLE" to template.root.title))
        rootEntity.save()
        createChildren(template.root, rootEntity)
        val templateEntity = ao.create(CustomTemplateAO::class.java, mapOf("NAME" to template.name, "ROOT_ID" to rootEntity.id))
        templateEntity.save()

        templateEntity.toCustomTemplate().let { println(it); it }
    }

    private fun createChildren(parent: CustomOutline, parentEntity: CustomOutlineAO) {
        parent.children.forEach { child ->
            val childEntity = ao.create(CustomOutlineAO::class.java, mapOf("TITLE" to child.title, "PARENT_ID" to parentEntity.id))
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
    @get:NotNull
    var root: CustomOutlineAO
}

fun CustomTemplateAO.toCustomTemplate() = CustomTemplate (
        name = this.name,
        root = this.root.toCustomOutline(),
        id = this.id
)

val CustomTemplateAO.thisAndAllOutlines: Array<Entity>
    get() {
        val entities = ArrayList<Entity>(127)
        fun addSelfAndDescendants(o: CustomOutlineAO) {
            for (c in o.children) {
                addSelfAndDescendants(c)
                entities += c
            }
            entities += o;
        }

        addSelfAndDescendants(this.root)
        entities += this

        return entities.toTypedArray()
    }

@Preload
interface CustomOutlineAO : Entity {
    @get:NotNull
    var title: String
    var parent: CustomOutlineAO?

    @get:OneToMany(reverse = "getParent")
    val children: Array<CustomOutlineAO>
}

fun CustomOutlineAO.toCustomOutline(): CustomOutline = CustomOutline(
        title = this.title,
        children = this.children.map { it.toCustomOutline() },
        id = this.id
)
//endregion

//region data
data class CustomTemplate
@JsonCreator constructor(
        @param:JsonProperty("name") @get:JsonProperty("name") val name: String,
        @param:JsonProperty("root") @get:JsonProperty("root") val root: CustomOutline,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: Int?
) {
    constructor(name: String, root: CustomOutline) : this(name, root, null)
}

data class CustomOutline
@JsonCreator constructor(
        @param:JsonProperty("title") @get:JsonProperty("title") val title: String,
        @param:JsonProperty("children") @get:JsonProperty("children") val children: List<CustomOutline>,
        @param:JsonProperty("id") @get:JsonProperty("id") val id: Int?
) {
    constructor(title: String, children: List<CustomOutline>) : this(title, children, null)
}
//endregion
