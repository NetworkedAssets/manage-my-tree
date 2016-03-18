import com.networkedassets.plugins.addpagetree.managepages.Command;
import junit.framework.Assert;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

public class CommandTest {
    @Test
    public void testSerializeCommand() throws IOException {
        final ObjectMapper om = new ObjectMapper();

        final Command ap = new Command.AddPage("a", "j1", "2");
        final String aps = om.writeValueAsString(ap);
        final Command apd = om.readValue(aps, Command.class);
        Assert.assertEquals(ap, apd);

        final Command rp = new Command.RemovePage("0");
        final String rps = om.writeValueAsString(rp);
        final Command rpd = om.readValue(rps, Command.class);
        Assert.assertEquals(rp, rpd);

        final Command mp = new Command.MovePage("1", null, 0);
        final String mps = om.writeValueAsString(mp);
        final Command mpd = om.readValue(mps, Command.class);
        Assert.assertEquals(mp, mpd);

        final Command rnp = new Command.RenamePage("42", "czterdzieści dwa");
        final String rnps = om.writeValueAsString(rnp);
        final Command rnpd = om.readValue(rnps, Command.class);
        Assert.assertEquals(rnp, rnpd);
    }
}
