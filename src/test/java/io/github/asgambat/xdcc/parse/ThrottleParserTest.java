package io.github.asgambat.xdcc.parse;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ThrottleParserTest {

    @Test
    void testUnlimited() {
        assertEquals(-1, ThrottleParser.parseThrottle(null));
        assertEquals(-1, ThrottleParser.parseThrottle(""));
        assertEquals(-1, ThrottleParser.parseThrottle("-1"));
        assertEquals(-1, ThrottleParser.parseThrottle("0"));
    }

    @Test
    void testKilobytes() {
        assertEquals(1024L, ThrottleParser.parseByteString("1K"));
        assertEquals(1024L, ThrottleParser.parseByteString("1KB"));
        assertEquals(512L * 1024, ThrottleParser.parseByteString("512K"));
    }

    @Test
    void testMegabytes() {
        assertEquals(1024L * 1024, ThrottleParser.parseByteString("1M"));
        assertEquals(1024L * 1024, ThrottleParser.parseByteString("1MB"));
    }

    @Test
    void testGigabytes() {
        assertEquals(1024L * 1024 * 1024, ThrottleParser.parseByteString("1G"));
        assertEquals(1024L * 1024 * 1024, ThrottleParser.parseByteString("1GB"));
    }

    @Test
    void testBytes() {
        assertEquals(100L, ThrottleParser.parseByteString("100"));
        assertEquals(100L, ThrottleParser.parseByteString("100B"));
    }

    @Test
    void testInvalidThrottle() {
        assertThrows(IllegalArgumentException.class, () -> ThrottleParser.parseByteString("abc"));
        assertThrows(IllegalArgumentException.class, () -> ThrottleParser.parseByteString(""));
    }
}
