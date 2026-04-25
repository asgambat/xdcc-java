package io.github.asgambat.xdcc.parse;

import io.github.asgambat.xdcc.domain.IrcServer;
import io.github.asgambat.xdcc.domain.XdccPack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class XdccMessageParser {

    private static final Pattern XDCC_MSG_PATTERN = Pattern.compile(
            "^/msg ([^ ]+) xdcc send #([0-9]+)((?:,[0-9]+)*|(?:-[0-9]+(?:;[0-9]+)?)?)$"
    );

    private XdccMessageParser() {}

    /**
     * Parses an XDCC message and returns one XDCCPack per requested pack number.
     * Accepted formats:
     *   /msg &lt;bot&gt; xdcc send #42        → single pack
     *   /msg &lt;bot&gt; xdcc send #1,3,5     → comma-separated list
     *   /msg &lt;bot&gt; xdcc send #1-10      → inclusive range
     *   /msg &lt;bot&gt; xdcc send #1-10;2    → range with step
     * server defaults to "irc.rizon.net" if empty; directory defaults to ".".
     */
    public static List<XdccPack> parse(String msg, String directory, String server) throws IllegalArgumentException {
        return parse(msg, directory, server, 20);
    }

    /**
     * Parses an XDCC message and returns one XDCCPack per requested pack number.
     * @param maxRange maximum allowed range size (0 or negative = unlimited)
     */
    public static List<XdccPack> parse(String msg, String directory, String server, int maxRange) throws IllegalArgumentException {
        if (msg == null || msg.isBlank()) {
            throw new IllegalArgumentException("Empty XDCC message");
        }
        Matcher m = XDCC_MSG_PATTERN.matcher(msg.trim());
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid XDCC message format: " + msg);
        }

        String bot = m.group(1);
        int firstPack = Integer.parseInt(m.group(2));
        String rest = m.group(3); // e.g. ",3,5" or "-10" or "-10;2" or ""

        IrcServer resolvedServer = resolveServer(bot, server == null ? "" : server);
        String dir = (directory == null || directory.isEmpty()) ? "." : directory;

        List<Integer> packNumbers = new ArrayList<>();

        if (rest == null || rest.isEmpty()) {
            // Single pack
            packNumbers.add(firstPack);
        } else if (rest.startsWith(",")) {
            // Comma list
            packNumbers.add(firstPack);
            for (String part : rest.substring(1).split(",")) {
                if (!part.isEmpty()) {
                    packNumbers.add(Integer.parseInt(part));
                }
            }
        } else if (rest.startsWith("-")) {
            // Range: -END or -END;STEP
            String rangeStr = rest.substring(1);
            int step = 1;
            int semicolon = rangeStr.indexOf(';');
            if (semicolon >= 0) {
                step = Integer.parseInt(rangeStr.substring(semicolon + 1));
                rangeStr = rangeStr.substring(0, semicolon);
            }
            int end = Integer.parseInt(rangeStr);
            if (step <= 0) step = 1;
            if (maxRange > 0 && end - firstPack > maxRange) {
                throw new IllegalArgumentException("Pack range too large (max " + maxRange + "): " + firstPack + "-" + end + ". Use --no-pack-limit to override.");
            }
            for (int i = firstPack; i <= end; i += step) {
                packNumbers.add(i);
            }
        } else {
            packNumbers.add(firstPack);
        }

        List<XdccPack> packs = new ArrayList<>();
        for (int num : packNumbers) {
            XdccPack pack = new XdccPack(resolvedServer, bot, num);
            pack.setDirectory(dir);
            packs.add(pack);
        }
        return packs;
    }

    public static IrcServer resolveServer(String bot, String defaultServer) {
        if (defaultServer != null && !defaultServer.isEmpty()
                && !defaultServer.equals("irc.rizon.net")) {
            return IrcServer.parse(defaultServer);
        }
        if (bot.startsWith("TLT")) {
            return IrcServer.of("irc.williamgattone.it");
        }
        if (bot.startsWith("WeC")) {
            return IrcServer.of("irc.explosionirc.net");
        }
        String srv = (defaultServer == null || defaultServer.isEmpty()) ? "irc.rizon.net" : defaultServer;
        return IrcServer.parse(srv);
    }

    public static void preparePacks(List<XdccPack> packs, String location) {
        for (XdccPack p : packs) {
            p.setServer(resolveServer(p.getBot(), p.getServer().address()));
        }
        if (location == null || location.isEmpty()) return;
        if (packs.size() == 1) {
            packs.get(0).setFilename(location, true);
        } else {
            for (int i = 0; i < packs.size(); i++) {
                packs.get(i).setFilename(String.format("%s-%03d", location, i), true);
            }
        }
    }
}
