import com.networkedassets.plugins.managemytree.Command;
import com.networkedassets.plugins.managemytree.JsonMessage;
import com.networkedassets.plugins.managemytree.Location;
import com.networkedassets.plugins.managemytree.OriginalPage;
import com.networkedassets.plugins.managemytree.commands.*;
import junit.framework.Assert;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

public class CommandTest {
    @Test
    public void testSerializeCommand() throws IOException {
        final ObjectMapper om = new ObjectMapper();

        String foo = om.writeValueAsString(new JsonMessage(500, "foo"));

        final AddPage ap = new AddPage("a", "j1", "2");
        ap.setConfluenceId(2L);
        final String aps = om.writeValueAsString(ap);
        final Command apd = om.readValue(aps, Command.class);
        Assert.assertEquals(ap, apd);

        final RemovePage rp = new RemovePage("0");
        rp.getRemovedPages().add(new OriginalPage(0, new Location(1, 2)));
        rp.setName("foo");
        final String rps = om.writeValueAsString(rp);
        final Command rpd = om.readValue(rps, Command.class);
        Assert.assertEquals(rp, rpd);

        final MovePage mp = new MovePage("1", null, 0);
        mp.setMovedPage(new OriginalPage(1, new Location(2, 3)));
        mp.setName("foo");
        final String mps = om.writeValueAsString(mp);
        final Command mpd = om.readValue(mps, Command.class);
        Assert.assertEquals(mp, mpd);

        final RenamePage rnp = new RenamePage("42", "czterdzieści dwa");
        rnp.setOldName("lel");
        rnp.setRenamedPageId(42L);
        final String rnps = om.writeValueAsString(rnp);
        final Command rnpd = om.readValue(rnps, Command.class);
        Assert.assertEquals(rnp, rnpd);

        final InsertTemplate inp = new InsertTemplate("foo", new Template.FromBlueprint("bar"));
        final String inps = om.writeValueAsString(inp);
        final Command inpd = om.readValue(inps, Command.class);
        Assert.assertEquals(inp, inpd);
    }
}
