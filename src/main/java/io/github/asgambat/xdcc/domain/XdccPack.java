package io.github.asgambat.xdcc.domain;

public class XdccPack {

    private IrcServer server;
    private String bot;
    private int packNumber;
    private String directory = ".";
    private String filename = "";
    private String originalFilename = "";
    private long size = 0;

    public XdccPack(IrcServer server, String bot, int packNumber) {
        this.server = server;
        this.bot = bot;
        this.packNumber = packNumber;
    }

    public static XdccPack of(IrcServer server, String bot, int packNumber) {
        return new XdccPack(server, bot, packNumber);
    }

    public void setFilename(String filename, boolean override) {
        if (this.filename != null && !this.filename.isEmpty() && !override) {
            // Only add extension if missing
            int dotIdx = filename.indexOf('.');
            if (dotIdx > 0) {
                String ext = filename.substring(dotIdx + 1);
                if (!this.filename.endsWith("." + ext)) {
                    this.filename += "." + ext;
                }
            }
            return;
        }
        this.filename = filename;
    }

    public void setOriginalFilename(String filename) {
        this.originalFilename = filename;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public boolean isFilenameValid(String filename) {
        if (originalFilename != null && !originalFilename.isEmpty()) {
            return filename.equals(originalFilename);
        }
        return true;
    }

    public String getFilepath() {
        if (directory == null || directory.isEmpty() || directory.equals(".")) {
            return filename;
        }
        return directory + "/" + filename;
    }

    public String getRequestMessage(boolean full) {
        String msg = "xdcc send #" + packNumber;
        if (full) {
            return "/msg " + bot + " " + msg;
        }
        return msg;
    }

    public static String humanReadableBytes(long b) {
        final long unit = 1024;
        if (b < unit) return b + " B";
        long div = unit;
        int exp = 0;
        for (long n = b / unit; n >= unit; n /= unit) {
            div *= unit;
            exp++;
        }
        return String.format(java.util.Locale.US, "%.1f %cB", (double) b / div, "KMGTPE".charAt(exp));
    }

    @Override
    public String toString() {
        return String.format("%s (/msg %s xdcc send #%d) [%s]",
                filename, bot, packNumber, humanReadableBytes(size));
    }

    // Getters
    public IrcServer getServer() { return server; }
    public String getBot() { return bot; }
    public int getPackNumber() { return packNumber; }
    public String getDirectory() { return directory; }
    public String getFilename() { return filename; }
    public String getOriginalFilename() { return originalFilename; }
    public long getSize() { return size; }

    // Setters
    public void setServer(IrcServer server) { this.server = server; }
    public void setBot(String bot) { this.bot = bot; }
    public void setPackNumber(int packNumber) { this.packNumber = packNumber; }
}
