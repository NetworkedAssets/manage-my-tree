
import com.google.common.collect.Lists
import com.networkedassets.plugins.managemytree.*
import com.networkedassets.plugins.managemytree.commands.*
import org.codehaus.jackson.map.ObjectMapper
import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test
import java.util.*

@Suppress("UNUSED_VARIABLE")
class SerializationTest {
    private val om = ObjectMapper()

    @Test fun testSerializeCommand() {
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

        val inp = InsertTemplatePart(
                "foo",
                TemplatePartId("bar", TemplateId.FromBlueprint("bar")),
                "2", 8)
        inp.insertedPageId = 2L
        val inps = om.writeValueAsString(inp)
        val inpd = om.readValue(inps, Command::class.java)
        assertEquals(inp, inpd)

        val inp2 = InsertTemplatePart(
                "foo",
                TemplatePartId("foo", TemplateId.Custom(42)),
                "1", 2)
        inp2.insertedPageId = 3L
        val inps2 = om.writeValueAsString(inp2)
        val inpd2 = om.readValue(inps2, Command::class.java)
        assertEquals(inp2, inpd2)
    }

    @Test fun testSerializeOutline() {
        val o = Outline("foo", "bar", Lists.newArrayList<Outline>(), null)
        //language=JSON
        val s = """{"title": "foo", "text": "bar", "children": []}"""
        val customOutline = om.readValue(s, Outline::class.java)
        assertEquals(customOutline, o)

        val t = Template("bar", listOf(o))
        val customTemplate = om.readValue(om.writeValueAsString(t), Template::class.java)
        assertEquals(customTemplate, t)
    }
}