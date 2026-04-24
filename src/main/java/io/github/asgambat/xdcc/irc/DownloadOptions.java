package io.github.asgambat.xdcc.irc;

public class DownloadOptions {
    private int connectTimeout = 120;   // seconds
    private int stallTimeout = 60;       // seconds, 0=disabled
    private String fallbackChannel = "";
    private long throttleBytes = -1;     // bytes/sec, -1=unlimited
    private int waitTime = 0;            // seconds before XDCC request
    private String username = "";        // empty=random
    private int channelJoinDelay = -1;   // seconds, -1=random 5-10
    private String dnsServer = "8.8.8.8:53";
    private int verbosity = 0;
    private String ircLibrary = "pircbotx";

    public DownloadOptions() {}

    // Getters
    public int getConnectTimeout() { return connectTimeout; }
    public int getStallTimeout() { return stallTimeout; }
    public String getFallbackChannel() { return fallbackChannel; }
    public long getThrottleBytes() { return throttleBytes; }
    public int getWaitTime() { return waitTime; }
    public String getUsername() { return username; }
    public int getChannelJoinDelay() { return channelJoinDelay; }
    public String getDnsServer() { return dnsServer; }
    public int getVerbosity() { return verbosity; }
    public String getIrcLibrary() { return ircLibrary; }

    // Setters
    public void setConnectTimeout(int connectTimeout) { this.connectTimeout = connectTimeout; }
    public void setStallTimeout(int stallTimeout) { this.stallTimeout = stallTimeout; }
    public void setFallbackChannel(String fallbackChannel) { this.fallbackChannel = fallbackChannel; }
    public void setThrottleBytes(long throttleBytes) { this.throttleBytes = throttleBytes; }
    public void setWaitTime(int waitTime) { this.waitTime = waitTime; }
    public void setUsername(String username) { this.username = username; }
    public void setChannelJoinDelay(int channelJoinDelay) { this.channelJoinDelay = channelJoinDelay; }
    public void setDnsServer(String dnsServer) { this.dnsServer = dnsServer; }
    public void setVerbosity(int verbosity) { this.verbosity = verbosity; }
    public void setIrcLibrary(String ircLibrary) { this.ircLibrary = ircLibrary; }

    public Builder builder() { return new Builder(); }

    public static class Builder {
        private final DownloadOptions opts = new DownloadOptions();

        public Builder connectTimeout(int v) { opts.connectTimeout = v; return this; }
        public Builder stallTimeout(int v) { opts.stallTimeout = v; return this; }
        public Builder fallbackChannel(String v) { opts.fallbackChannel = v; return this; }
        public Builder throttleBytes(long v) { opts.throttleBytes = v; return this; }
        public Builder waitTime(int v) { opts.waitTime = v; return this; }
        public Builder username(String v) { opts.username = v; return this; }
        public Builder channelJoinDelay(int v) { opts.channelJoinDelay = v; return this; }
        public Builder dnsServer(String v) { opts.dnsServer = v; return this; }
        public Builder verbosity(int v) { opts.verbosity = v; return this; }
        public Builder ircLibrary(String v) { opts.ircLibrary = v; return this; }
        public DownloadOptions build() { return opts; }
    }
}
