package io.github.asgambat.xdcc.command;

import io.github.asgambat.xdcc.domain.XdccPack;
import io.github.asgambat.xdcc.downloader.XdccDownloader;
import io.github.asgambat.xdcc.irc.DownloadOptions;
import io.github.asgambat.xdcc.parse.ThrottleParser;
import io.github.asgambat.xdcc.parse.XdccMessageParser;
import io.github.asgambat.xdcc.search.SearchEngine;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Command(name = "xdcc-browse", description = "Search and interactively download XDCC packs", mixinStandardHelpOptions = true)
public class BrowseCommand implements Runnable {

    @Parameters(index = "0", description = "Search term")
    private String term;

    @Option(names = {"-e", "--search-engine"}, description = "Search engine (default: xdcc-eu)", defaultValue = "xdcc-eu")
    private String engineName;

    @Option(names = {"-x", "--ext"}, description = "Filter by extensions (comma separated, e.g. mkv,avi)", defaultValue = "")
    private String ext;

    @Option(names = {"-b", "--bot"}, description = "Filter by bot name substring (case-insensitive)", defaultValue = "")
    private String bot;

    // Download options
    @Option(names = {"-s", "--server"}, description = "IRC server override", defaultValue = "irc.rizon.net")
    private String server;

    @Option(names = {"-o", "--out"}, description = "Output directory or file", defaultValue = "")
    private String out;

    @Option(names = {"-t", "--throttle"}, description = "Speed limit", defaultValue = "-1")
    private String throttle;

    @Option(names = {"-c", "--connect-timeout"}, description = "Connect timeout seconds", defaultValue = "120")
    private int connectTimeout;

    @Option(names = {"-S", "--stall-timeout"}, description = "Stall timeout seconds", defaultValue = "60")
    private int stallTimeout;

    @Option(names = {"-f", "--fallback-channel"}, description = "Fallback channel", defaultValue = "")
    private String fallbackChannel;

    @Option(names = {"-w", "--wait-time"}, description = "Wait time before XDCC request", defaultValue = "0")
    private int waitTime;

    @Option(names = {"-u", "--username"}, description = "IRC nick", defaultValue = "")
    private String username;

    @Option(names = {"-d", "--channel-join-delay"}, description = "Channel join delay", defaultValue = "-1")
    private int channelJoinDelay;

    @Option(names = {"--dns-server"}, description = "Fallback DNS server", defaultValue = "")
    private String dnsServer;

    @Option(names = {"-v"}, description = "Verbose", defaultValue = "false")
    private boolean v;

    @Option(names = {"-vv"}, description = "Very verbose", defaultValue = "false")
    private boolean vv;

    @Option(names = {"-q"}, description = "Quiet", defaultValue = "false")
    private boolean q;

    @Option(names = {"-qq"}, description = "Very quiet", defaultValue = "false")
    private boolean qq;

    @Option(names = {"--irc-library"}, description = "IRC library to use: pircbotx or kitteh (default: pircbotx)", defaultValue = "pircbotx")
    private String ircLibrary;

    @Inject
    private Collection<SearchEngine> engines;

    @Inject
    private XdccDownloader downloader;

    @Override
    public void run() {
        SearchEngine engine = engines.stream()
                .filter(e -> e.name().equals(engineName))
                .findFirst()
                .orElse(null);

        if (engine == null) {
            System.err.println("Unknown search engine: " + engineName);
            System.exit(1);
            return;
        }

        List<XdccPack> results;
        try {
            results = engine.search(term);
        } catch (IOException e) {
            System.err.println("Search failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Apply filters
        if (!ext.isEmpty()) {
            String[] exts = ext.split(",");
            results = results.stream()
                    .filter(p -> {
                        String fn = p.getFilename().toLowerCase();
                        for (String e : exts) {
                            if (fn.endsWith("." + e.trim().toLowerCase())) return true;
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        if (!bot.isEmpty()) {
            String botFilter = bot.toLowerCase();
            results = results.stream()
                    .filter(p -> p.getBot().toLowerCase().contains(botFilter))
                    .collect(Collectors.toList());
        }

        if (results.isEmpty()) {
            System.out.println("No results found.");
            return;
        }

        // Print results
        for (int i = 0; i < results.size(); i++) {
            XdccPack pack = results.get(i);
            System.out.printf("%d. %s [%s] (%s #%d)%n",
                    i + 1,
                    pack.getFilename(),
                    io.github.asgambat.xdcc.domain.XdccPack.humanReadableBytes(pack.getSize()),
                    pack.getBot(),
                    pack.getPackNumber());
        }

        // Interactive selection
        List<XdccPack> selected = promptSelection(results);
        if (selected.isEmpty()) return;

        int verbosity = vv ? 2 : v ? 1 : qq ? -2 : q ? -1 : 0;

        // Apply output path
        if (!out.isEmpty()) {
            XdccMessageParser.preparePacks(selected, out);
        }

        DownloadOptions opts = new DownloadOptions();
        opts.setConnectTimeout(connectTimeout);
        opts.setStallTimeout(stallTimeout);
        opts.setFallbackChannel(fallbackChannel);
        opts.setWaitTime(waitTime);
        opts.setUsername(username);
        opts.setChannelJoinDelay(channelJoinDelay);
        opts.setVerbosity(verbosity);
        opts.setIrcLibrary(ircLibrary);
        if (dnsServer != null && !dnsServer.isEmpty()) opts.setDnsServer(dnsServer);
        try {
            opts.setThrottleBytes(ThrottleParser.parseThrottle(throttle));
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid throttle: " + throttle);
        }

        downloader.downloadPacks(selected, opts);
    }

    private List<XdccPack> promptSelection(List<XdccPack> results) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("Enter selection (number, range 1-N, count 1+5, list 1,3,5, or 'all'): ");
            System.out.flush();
            String input;
            try {
                input = reader.readLine();
            } catch (IOException e) {
                return List.of();
            }
            if (input == null || input.isBlank()) continue;
            input = input.trim();

            try {
                if (input.equals("all")) {
                    return results;
                } else if (input.contains(",")) {
                    // Comma list
                    List<XdccPack> selected = new ArrayList<>();
                    for (String part : input.split(",")) {
                        int idx = Integer.parseInt(part.trim()) - 1;
                        if (idx >= 0 && idx < results.size()) selected.add(results.get(idx));
                    }
                    return selected;
                } else if (input.contains("-")) {
                    // Range
                    String[] parts = input.split("-");
                    int start = Integer.parseInt(parts[0].trim()) - 1;
                    int end = Integer.parseInt(parts[1].trim()) - 1;
                    List<XdccPack> selected = new ArrayList<>();
                    for (int i = start; i <= end && i < results.size(); i++) {
                        if (i >= 0) selected.add(results.get(i));
                    }
                    return selected;
                } else if (input.contains("+")) {
                    // Count: start+count
                    String[] parts = input.split("\\+");
                    int start = Integer.parseInt(parts[0].trim()) - 1;
                    int count = Integer.parseInt(parts[1].trim());
                    List<XdccPack> selected = new ArrayList<>();
                    for (int i = start; i < start + count && i < results.size(); i++) {
                        if (i >= 0) selected.add(results.get(i));
                    }
                    return selected;
                } else {
                    // Single number
                    int idx = Integer.parseInt(input) - 1;
                    if (idx >= 0 && idx < results.size()) {
                        return List.of(results.get(idx));
                    }
                }
            } catch (NumberFormatException e) {
                // fall through to re-prompt
            }
            System.out.println("Invalid selection. Please try again.");
        }
    }
}
