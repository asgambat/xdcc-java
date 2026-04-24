package io.github.asgambat.xdcc.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class XdccPackTest {

    @Test
    void testHumanReadableBytes() {
        assertEquals("0 B", XdccPack.humanReadableBytes(0));
        assertEquals("1023 B", XdccPack.humanReadableBytes(1023));
        assertEquals("1.0 KB", XdccPack.humanReadableBytes(1024));
        assertEquals("1.0 MB", XdccPack.humanReadableBytes(1024 * 1024));
        assertEquals("1.0 GB", XdccPack.humanReadableBytes(1024L * 1024 * 1024));
    }

    @Test
    void testGetFilepath() {
        XdccPack pack = new XdccPack(IrcServer.of("irc.rizon.net"), "Bot", 1);
        pack.setFilename("test.mkv", true);

        // Default directory
        assertEquals("test.mkv", pack.getFilepath());

        // With directory
        pack.setDirectory("/downloads");
        assertEquals("/downloads/test.mkv", pack.getFilepath());
    }

    @Test
    void testGetRequestMessage() {
        XdccPack pack = new XdccPack(IrcServer.of("irc.rizon.net"), "MyBot", 42);
        assertEquals("xdcc send #42", pack.getRequestMessage(false));
        assertEquals("/msg MyBot xdcc send #42", pack.getRequestMessage(true));
    }

    @Test
    void testIrcServerParse() {
        IrcServer s1 = IrcServer.parse("irc.rizon.net");
        assertEquals("irc.rizon.net", s1.address());
        assertEquals(6667, s1.port());

        IrcServer s2 = IrcServer.parse("irc.rizon.net:6697");
        assertEquals("irc.rizon.net", s2.address());
        assertEquals(6697, s2.port());

        IrcServer s3 = IrcServer.parse("[::1]:6667");
        assertEquals("::1", s3.address());
        assertEquals(6667, s3.port());
    }
}
