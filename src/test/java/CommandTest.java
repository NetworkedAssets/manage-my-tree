import com.google.common.collect.Lists;
import com.networkedassets.plugins.managemytree.*;
import com.networkedassets.plugins.managemytree.commands.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class CommandTest {
    private final ObjectMapper om = new ObjectMapper();

    @Test
    public void testSerializeCommand() throws IOException {
        final AddPage ap = new AddPage("a", "j1", "2");
        ap.setConfluenceId(2L);
        final String aps = om.writeValueAsString(ap);
        final Command apd = om.readValue(aps, Command.class);
        assertEquals(ap, apd);

        final RemovePage rp = new RemovePage("0");
        rp.getRemovedPages().add(new OriginalPage(0, new Location(1, 2)));
        rp.setName("foo");
        final String rps = om.writeValueAsString(rp);
        final Command rpd = om.readValue(rps, Command.class);
        assertEquals(rp, rpd);

        final MovePage mp = new MovePage("1", null, 0);
        mp.setMovedPage(new OriginalPage(1, new Location(2, 3)));
        mp.setName("foo");
        final String mps = om.writeValueAsString(mp);
        final Command mpd = om.readValue(mps, Command.class);
        assertEquals(mp, mpd);

        final RenamePage rnp = new RenamePage("42", "czterdzieści dwa");
        rnp.setOldName("lel");
        rnp.setRenamedPageId(42L);
        final String rnps = om.writeValueAsString(rnp);
        final Command rnpd = om.readValue(rnps, Command.class);
        assertEquals(rnp, rnpd);

        final InsertTemplate inp = new InsertTemplate(
                "foo",
                new TemplateId.FromBlueprint("bar"),
                Collections.singletonMap(2, "b")
        );
        inp.getInsertedPages().add(2L);
        final String inps = om.writeValueAsString(inp);
        final Command inpd = om.readValue(inps, Command.class);
        assertEquals(inp, inpd);

        final InsertTemplate inp2 = new InsertTemplate(
                "foo",
                new TemplateId.Custom(42),
                Collections.singletonMap(1, "a")
        );
        inp2.getInsertedPages().add(3L);
        final String inps2 = om.writeValueAsString(inp2);
        final Command inpd2 = om.readValue(inps2, Command.class);
        assertEquals(inp2, inpd2);
    }

    @Test
    public void testSerializeOutline() throws IOException {
        final CustomOutline o = new CustomOutline("foo", Lists.newArrayList(), null);
        final String s = "{\"title\": \"foo\", \"children\": []}";
        CustomOutline customOutline = om.readValue(s, CustomOutline.class);
        assertEquals(customOutline, o);

        final CustomTemplate t = new CustomTemplate("bar", o);
        CustomTemplate customTemplate = om.readValue(om.writeValueAsString(t), CustomTemplate.class);
        assertEquals(customTemplate, t);
    }
}
