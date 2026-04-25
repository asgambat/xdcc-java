package io.github.asgambat.xdcc.domain;

import io.github.asgambat.xdcc.irc.XdccError;

public sealed interface PackResult permits PackResult.Success, PackResult.Failure {

    record Success(String filePath) implements PackResult {}
    record Failure(XdccError error, String lastBotNotice) implements PackResult {}

    static PackResult success(String filePath) {
        return new Success(filePath);
    }

    static PackResult failure(XdccError error, String botNotice) {
        return new Failure(error, botNotice != null ? botNotice : "");
    }
}
