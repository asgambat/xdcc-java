package io.github.asgambat.xdcc.irc;

public class XdccError extends RuntimeException {

    public enum Kind {
        TIMEOUT,
        BOT_NOT_FOUND,
        PACK_ALREADY_REQUESTED,
        ALREADY_DOWNLOADED,
        BOT_DENIED,
        SERVER_UNREACHABLE,
        UNRECOVERABLE,
        DOWNLOAD_FAILED
    }

    private final Kind kind;

    public XdccError(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public XdccError(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public boolean is(Kind other) {
        return this.kind == other;
    }

    public static final XdccError TIMEOUT = new XdccError(Kind.TIMEOUT, "download timed out");
    public static final XdccError BOT_NOT_FOUND = new XdccError(Kind.BOT_NOT_FOUND, "bot does not exist on server");
    public static final XdccError PACK_ALREADY_REQUESTED = new XdccError(Kind.PACK_ALREADY_REQUESTED, "pack already requested");
    public static final XdccError ALREADY_DOWNLOADED = new XdccError(Kind.ALREADY_DOWNLOADED, "file already downloaded");
    public static final XdccError BOT_DENIED = new XdccError(Kind.BOT_DENIED, "bot denied request");
    public static final XdccError UNRECOVERABLE = new XdccError(Kind.UNRECOVERABLE, "unrecoverable error");
    public static final XdccError DOWNLOAD_FAILED = new XdccError(Kind.DOWNLOAD_FAILED, "download failed");

    public static XdccError serverUnreachable(String message) {
        return new XdccError(Kind.SERVER_UNREACHABLE, message);
    }

    public static XdccError serverUnreachable(String message, Throwable cause) {
        return new XdccError(Kind.SERVER_UNREACHABLE, message, cause);
    }

    public static XdccError botDenied(String message) {
        return new XdccError(Kind.BOT_DENIED, message);
    }

    public static XdccError downloadFailed(String message) {
        return new XdccError(Kind.DOWNLOAD_FAILED, message);
    }
}
