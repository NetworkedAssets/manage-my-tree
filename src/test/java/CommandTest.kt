
import com.google.common.collect.Lists
import com.networkedassets.plugins.managemytree.*
import com.networkedassets.plugins.managemytree.commands.*
import org.codehaus.jackson.map.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.*

class CommandTest {
    private val om = ObjectMapper()

    @Test
    fun testSerializeCommand() {
        val ap = AddPage("a", "j1", "2")
        ap.confluenceId = 2L
        val aps = om.writeValueAsString(ap)
        val apd = om.readValue(aps, Command::class.java)
        assertEquals(ap, apd)

        val rp = RemovePage("0")
        rp.removedPages.add(OriginalPage(0, Location(1, 2)))
        rp.name = "foo"
        val rps = om.writeValueAsString(rp)
        val rpd = om.readValue(rps, Command::class.java)
        assertEquals(rp, rpd)

        val mp = MovePage("1", null, 0)
        mp.movedPage = OriginalPage(1, Location(2, 3))
        mp.name = "foo"
        val mps = om.writeValueAsString(mp)
        val mpd = om.readValue(mps, Command::class.java)
        assertEquals(mp, mpd)

        val rnp = RenamePage("42", "czterdzieści dwa")
        rnp.oldName = "lel"
        rnp.renamedPageId = 42L
        val rnps = om.writeValueAsString(rnp)
        val rnpd = om.readValue(rnps, Command::class.java)
        assertEquals(rnp, rnpd)

        val inp = InsertTemplate(
                "foo",
                TemplateId.FromBlueprint("bar"),
                Collections.singletonMap("2", "b"))
        inp.insertedPages.add(2L)
        val inps = om.writeValueAsString(inp)
        val inpd = om.readValue(inps, Command::class.java)
        assertEquals(inp, inpd)

        val inp2 = InsertTemplate(
                "foo",
                TemplateId.Custom(42),
                Collections.singletonMap("2", "a"))
        inp2.insertedPages.add(3L)
        val inps2 = om.writeValueAsString(inp2)
        val inpd2 = om.readValue(inps2, Command::class.java)
        assertEquals(inp2, inpd2)
    }

    @Test
    fun testSerializeOutline() {
        val o = CustomOutline("foo", Lists.newArrayList<CustomOutline>(), null)
        //language=JSON
        val s = """{"title": "foo", "children": []}"""
        val customOutline = om.readValue(s, CustomOutline::class.java)
        assertEquals(customOutline, o)

        val t = CustomTemplate("bar", listOf(o))
        val customTemplate = om.readValue(om.writeValueAsString(t), CustomTemplate::class.java)
        assertEquals(customTemplate, t)
    }

    @Test
    fun testSerializeKotlinObjectLiterals() {
        val s = om.writeValueAsString(object { val name = "foo"; val id = 2; })
        assertEquals(s, """{"name":"foo","id":2}""")
    }

    @Test
    fun testDeserializeInsertTemplate() {
        //language=JSON
        val s = """
            {
                "commandType": "insertTemplate",
                "templateId": {
                    "templateType": "custom",
                    "customOutlineId": 1
                },
                "parentId": "98310",
                "newPageJstreeIds": {
                    "1": "j1_11",
                    "2": "j1_12"
                },
                "name": "test"
            }
        """;

        val cmds: Command = om.deserialize(s);
    }

}

inline fun <reified T: Any> ObjectMapper.deserialize(s: String): T =
        this.readValue(s, T::class.java)
