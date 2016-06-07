package com.networkedassets.plugins.managemytree

import com.atlassian.activeobjects.external.ActiveObjects
import net.java.ao.Entity
import net.java.ao.OneToMany
import net.java.ao.Preload
import net.java.ao.schema.NotNull
import org.codehaus.jackson.annotate.*
import javax.ws.rs.Path
import kotlin.properties.Delegates.notNull

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
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as FromBlueprint

            if (spaceBlueprintId != other.spaceBlueprintId) return false

            return true
        }

        override fun hashCode(): Int{
            return spaceBlueprintId.hashCode()
        }

        override fun toString(): String{
            return "FromBlueprint(spaceBlueprintId='$spaceBlueprintId')"
        }
        //endregion
    }

    @JsonTypeName("custom")
    class Custom
    @JsonCreator constructor(
            @param:JsonProperty("customOutlineId") val customOutlineId: Int
    ) : TemplateId() {
        fun getFromDb() = CustomTemplateManager.getById(customOutlineId)
        //region equals, hashcode, toString
        override fun equals(other: Any?): Boolean{
            if (this === other) return true
            if (other?.javaClass != javaClass) return false

            other as Custom

            if (customOutlineId != other.customOutlineId) return false

            return true
        }

        override fun hashCode(): Int{
            return customOutlineId
        }

        override fun toString(): String{
            return "Custom(customOutlineId=$customOutlineId)"
        }
        //endregion
    }
}

@Path("/templates")
class TemplateService

class CustomTemplateManager(val ao: ActiveObjects) {
    fun getById(id: Int) = ao.get(CustomTemplateEntity::class.java, id).toCustomTemplate()
    fun create(template: CustomTemplate) = ao.executeInTransaction {
        val rootEntity = ao.create(CustomOutlineEntity::class.java, mapOf("TITLE" to template.root.title))
        rootEntity.save()
        createChildren(template.root, rootEntity)
        val templateEntity = ao.create(CustomTemplateEntity::class.java, mapOf("NAME" to template.name, "ROOT_ID" to rootEntity.id))
        templateEntity.save()
    }

    private fun createChildren(parent: CustomOutline, parentEntity: CustomOutlineEntity) {
        parent.children.forEach { child ->
            val childEntity = ao.create(CustomOutlineEntity::class.java, mapOf("TITLE" to child.title))
            childEntity.parent = parentEntity
            createChildren(child, childEntity)
        }
    }

    //region singleton stuff
    init {
        _instance = this
    }
    companion object {
        private var _instance: CustomTemplateManager by notNull()
        val instance: CustomTemplateManager
            get() = _instance

        fun getById(id: Int) = _instance.getById(id)
    }
    //endregion
}

@Preload
interface CustomTemplateEntity: Entity {
    @get:NotNull
    var name: String
    @get:NotNull
    var root: CustomOutlineEntity
}

fun CustomTemplateEntity.toCustomTemplate() = CustomTemplate (
        name = this.name,
        root = this.root.toCustomOutline()
)

@Preload
interface CustomOutlineEntity : Entity {
    @get:NotNull
    var title: String
    var parent: CustomOutlineEntity?

    @get:OneToMany(reverse = "getParent")
    val children: Array<out CustomOutlineEntity>
}

fun CustomOutlineEntity.toCustomOutline(): CustomOutline = CustomOutline(
        title = this.title,
        children = this.children.map { it.toCustomOutline() }
)


data class CustomTemplate
@JsonCreator constructor(
        @param:JsonProperty("name") val name: String,
        @param:JsonProperty("root") val root: CustomOutline
)

data class CustomOutline
@JsonCreator constructor(
        @param:JsonProperty("title") val title: String,
        @param:JsonProperty("children") val children: List<CustomOutline>
)
