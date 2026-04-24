package io.github.asgambat.xdcc.command;

import io.github.asgambat.xdcc.domain.XdccPack;
import io.github.asgambat.xdcc.downloader.XdccDownloader;
import io.github.asgambat.xdcc.irc.DownloadOptions;
import io.github.asgambat.xdcc.parse.ThrottleParser;
import io.github.asgambat.xdcc.parse.XdccMessageParser;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "xdcc-dl", description = "Download files via XDCC", mixinStandardHelpOptions = true)
public class DlCommand implements Runnable {

    @Parameters(index = "0", description = "XDCC message (e.g. /msg BotName xdcc send #42)")
    private String message;

    @Option(names = {"-s", "--server"}, description = "IRC server (default: irc.rizon.net)", defaultValue = "irc.rizon.net")
    private String server;

    @Option(names = {"-o", "--out"}, description = "Output directory or file", defaultValue = "")
    private String out;

    @Option(names = {"-t", "--throttle"}, description = "Speed limit (default: -1 = unlimited)", defaultValue = "-1")
    private String throttle;

    @Option(names = {"-c", "--connect-timeout"}, description = "Connect timeout in seconds", defaultValue = "120")
    private int connectTimeout;

    @Option(names = {"-S", "--stall-timeout"}, description = "Stall timeout in seconds", defaultValue = "60")
    private int stallTimeout;

    @Option(names = {"-f", "--fallback-channel"}, description = "Fallback channel if WHOIS returns nothing", defaultValue = "")
    private String fallbackChannel;

    @Option(names = {"-w", "--wait-time"}, description = "Seconds to wait before sending XDCC request", defaultValue = "0")
    private int waitTime;

    @Option(names = {"-u", "--username"}, description = "IRC nick (random if not set)", defaultValue = "")
    private String username;

    @Option(names = {"-d", "--channel-join-delay"}, description = "Seconds to wait after joining channel (-1 = random 5-10)", defaultValue = "-1")
    private int channelJoinDelay;

    @Option(names = {"--dns-server"}, description = "Fallback DNS server (default: 8.8.8.8:53)", defaultValue = "")
    private String dnsServer;

    @Option(names = {"-v"}, description = "Verbose output", defaultValue = "false")
    private boolean v;

    @Option(names = {"-vv"}, description = "Very verbose output", defaultValue = "false")
    private boolean vv;

    @Option(names = {"-q"}, description = "Quiet output", defaultValue = "false")
    private boolean q;

    @Option(names = {"-qq"}, description = "Very quiet output", defaultValue = "false")
    private boolean qq;

    @Option(names = {"--irc-library"}, description = "IRC library to use: pircbotx or kitteh (default: pircbotx)", defaultValue = "pircbotx")
    private String ircLibrary;

    @Inject
    private XdccDownloader downloader;

    @Override
    public void run() {
        int verbosity = vv ? 2 : v ? 1 : qq ? -2 : q ? -1 : 0;

        List<XdccPack> packs;
        try {
            packs = XdccMessageParser.parse(message, out.isEmpty() ? null : out, server);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        // If out is set and we want to specify an output file/dir
        if (!out.isEmpty()) {
            XdccMessageParser.preparePacks(packs, out);
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

        if (dnsServer != null && !dnsServer.isEmpty()) {
            opts.setDnsServer(dnsServer);
        }

        try {
            long throttleBytes = ThrottleParser.parseThrottle(throttle);
            opts.setThrottleBytes(throttleBytes);
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid throttle value: " + throttle);
            System.exit(1);
            return;
        }

        downloader.downloadPacks(packs, opts);
    }
}
