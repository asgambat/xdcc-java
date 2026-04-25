package io.github.asgambat.xdcc.command;

import io.github.asgambat.xdcc.irc.DownloadOptions;
import io.github.asgambat.xdcc.parse.ThrottleParser;
import picocli.CommandLine.Option;

/**
 * PicoCLI @Mixin that holds the shared download options used by both
 * DlCommand and BrowseCommand. Avoids duplicating ~45 lines of @Option declarations.
 */
public class DownloadOptionsMixin {

    @Option(names = {"-s", "--server"}, description = "IRC server (default: irc.rizon.net)", defaultValue = "irc.rizon.net")
    public String server;

    @Option(names = {"-o", "--out"}, description = "Output directory or file", defaultValue = "")
    public String out;

    @Option(names = {"-t", "--throttle"}, description = "Speed limit (default: -1 = unlimited)", defaultValue = "-1")
    public String throttle;

    @Option(names = {"-c", "--connect-timeout"}, description = "Connect timeout in seconds", defaultValue = "120")
    public int connectTimeout;

    @Option(names = {"-S", "--stall-timeout"}, description = "Stall timeout in seconds", defaultValue = "60")
    public int stallTimeout;

    @Option(names = {"-f", "--fallback-channel"}, description = "Fallback channel if WHOIS returns nothing", defaultValue = "")
    public String fallbackChannel;

    @Option(names = {"-w", "--wait-time"}, description = "Seconds to wait before sending XDCC request", defaultValue = "0")
    public int waitTime;

    @Option(names = {"-u", "--username"}, description = "IRC nick (random if not set)", defaultValue = "")
    public String username;

    @Option(names = {"-d", "--channel-join-delay"}, description = "Seconds to wait after joining channel (-1 = random 5-10)", defaultValue = "-1")
    public int channelJoinDelay;

    @Option(names = {"--dns-server"}, description = "Fallback DNS server (default: 8.8.8.8:53)", defaultValue = "")
    public String dnsServer;

    @Option(names = {"-v"}, description = "Verbose output", defaultValue = "false")
    public boolean v;

    @Option(names = {"-vv"}, description = "Very verbose output", defaultValue = "false")
    public boolean vv;

    @Option(names = {"-q"}, description = "Quiet output", defaultValue = "false")
    public boolean q;

    @Option(names = {"-qq"}, description = "Very quiet output", defaultValue = "false")
    public boolean qq;

    @Option(names = {"--irc-library"}, description = "IRC library to use: pircbotx or kitteh (default: pircbotx)", defaultValue = "pircbotx")
    public String ircLibrary;

    public int verbosity() {
        return vv ? 2 : v ? 1 : qq ? -2 : q ? -1 : 0;
    }

    /**
     * Builds a DownloadOptions from the mixin fields.
     * @return configured options, or null if throttle is invalid (error already printed)
     */
    public DownloadOptions buildOptions() {
        DownloadOptions opts = new DownloadOptions();
        opts.setConnectTimeout(connectTimeout);
        opts.setStallTimeout(stallTimeout);
        opts.setFallbackChannel(fallbackChannel);
        opts.setWaitTime(waitTime);
        opts.setUsername(username);
        opts.setChannelJoinDelay(channelJoinDelay);
        opts.setVerbosity(verbosity());
        opts.setIrcLibrary(ircLibrary);
        if (dnsServer != null && !dnsServer.isEmpty()) {
            opts.setDnsServer(dnsServer);
        }
        try {
            opts.setThrottleBytes(ThrottleParser.parseThrottle(throttle));
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid throttle value: " + throttle);
            return null;
        }
        return opts;
    }
}
