package io.github.asgambat.xdcc.domain;

import io.github.asgambat.xdcc.irc.XdccError;

public record PackResult(String filePath, XdccError error, String lastBotNotice) {

    public boolean isSuccess() {
        return error == null;
    }

    public static PackResult success(String filePath) {
        return new PackResult(filePath, null, null);
    }

    public static PackResult failure(XdccError error, String botNotice) {
        return new PackResult(null, error, botNotice);
    }
}
