package io.github.asgambat.xdcc.parse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import io.github.asgambat.xdcc.domain.XdccPack;
import java.util.List;

class XdccMessageParserTest {

    @Test
    void testSinglePack() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg TestBot xdcc send #42", ".", "");
        assertEquals(1, packs.size());
        assertEquals("TestBot", packs.get(0).getBot());
        assertEquals(42, packs.get(0).getPackNumber());
    }

    @Test
    void testCommaList() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg TestBot xdcc send #1,3,5", ".", "");
        assertEquals(3, packs.size());
        assertEquals(1, packs.get(0).getPackNumber());
        assertEquals(3, packs.get(1).getPackNumber());
        assertEquals(5, packs.get(2).getPackNumber());
    }

    @Test
    void testRange() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg TestBot xdcc send #1-5", ".", "");
        assertEquals(5, packs.size());
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, packs.get(i).getPackNumber());
        }
    }

    @Test
    void testRangeWithStep() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg TestBot xdcc send #1-10;2", ".", "");
        assertEquals(5, packs.size());
        assertEquals(1, packs.get(0).getPackNumber());
        assertEquals(3, packs.get(1).getPackNumber());
        assertEquals(5, packs.get(2).getPackNumber());
    }

    @Test
    void testInvalidFormat() {
        assertThrows(IllegalArgumentException.class, () ->
                XdccMessageParser.parse("/msg bot send #42", ".", ""));
    }

    @Test
    void testServerResolutionTLT() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg TLTBot xdcc send #1", ".", "");
        assertEquals("irc.williamgattone.it", packs.get(0).getServer().address());
    }

    @Test
    void testServerResolutionExplicit() {
        List<XdccPack> packs = XdccMessageParser.parse("/msg SomeBot xdcc send #1", ".", "irc.example.com:6697");
        assertEquals("irc.example.com", packs.get(0).getServer().address());
        assertEquals(6697, packs.get(0).getServer().port());
    }
}
