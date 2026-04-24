package io.github.asgambat.xdcc.domain;

public record IrcServer(String address, int port) {

    public static IrcServer of(String address) {
        return new IrcServer(address, 6667);
    }

    public static IrcServer of(String address, int port) {
        return new IrcServer(address, port);
    }

    public static IrcServer parse(String s) {
        if (s == null || s.isEmpty()) {
            return new IrcServer("irc.rizon.net", 6667);
        }
        // IPv6 with brackets like [::1]:6667
        if (s.startsWith("[")) {
            int closeBracket = s.indexOf(']');
            if (closeBracket > 0 && closeBracket + 1 < s.length() && s.charAt(closeBracket + 1) == ':') {
                String host = s.substring(1, closeBracket);
                String portStr = s.substring(closeBracket + 2);
                try {
                    int port = Integer.parseInt(portStr);
                    if (port > 0 && port <= 65535) {
                        return new IrcServer(host, port);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return new IrcServer(s, 6667);
        }
        // Count colons: 0 = hostname only, 1 = host:port, >1 = IPv6 without brackets
        long colons = s.chars().filter(c -> c == ':').count();
        if (colons == 0) {
            return new IrcServer(s, 6667);
        }
        if (colons == 1) {
            int idx = s.lastIndexOf(':');
            String host = s.substring(0, idx);
            String portStr = s.substring(idx + 1);
            try {
                int port = Integer.parseInt(portStr);
                if (port > 0 && port <= 65535) {
                    return new IrcServer(host, port);
                }
            } catch (NumberFormatException ignored) {}
            return new IrcServer(s, 6667);
        }
        // IPv6 without brackets
        return new IrcServer(s, 6667);
    }

    @Override
    public String toString() {
        return address + ":" + port;
    }
}
