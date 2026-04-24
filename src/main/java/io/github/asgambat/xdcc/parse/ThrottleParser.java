package io.github.asgambat.xdcc.parse;

public class ThrottleParser {

    private ThrottleParser() {}

    /**
     * Parses a throttle string.
     * Returns -1 if empty, "-1", or "0" (unlimited).
     * Supports K, M, G suffixes (1024-based).
     */
    public static long parseThrottle(String s) throws IllegalArgumentException {
        if (s == null || s.isBlank() || s.equals("-1") || s.equals("0")) {
            return -1;
        }
        return parseByteString(s);
    }

    /**
     * Parses a byte count string with optional suffix.
     * Supports: B, KB, MB, GB, K, M, G (1024-based).
     */
    public static long parseByteString(String s) throws IllegalArgumentException {
        if (s == null || s.isBlank()) {
            throw new IllegalArgumentException("Empty byte string");
        }
        s = s.trim().toUpperCase();

        long multiplier = 1;
        String numPart = s;

        if (s.endsWith("GB") || s.endsWith("G")) {
            multiplier = 1024L * 1024 * 1024;
            numPart = s.endsWith("GB") ? s.substring(0, s.length() - 2) : s.substring(0, s.length() - 1);
        } else if (s.endsWith("MB") || s.endsWith("M")) {
            multiplier = 1024L * 1024;
            numPart = s.endsWith("MB") ? s.substring(0, s.length() - 2) : s.substring(0, s.length() - 1);
        } else if (s.endsWith("KB") || s.endsWith("K")) {
            multiplier = 1024L;
            numPart = s.endsWith("KB") ? s.substring(0, s.length() - 2) : s.substring(0, s.length() - 1);
        } else if (s.endsWith("B")) {
            numPart = s.substring(0, s.length() - 1);
        }

        numPart = numPart.trim();
        if (numPart.isEmpty()) {
            throw new IllegalArgumentException("No numeric value in: " + s);
        }
        try {
            long value = Long.parseLong(numPart);
            return value * multiplier;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid byte string: " + s);
        }
    }
}
