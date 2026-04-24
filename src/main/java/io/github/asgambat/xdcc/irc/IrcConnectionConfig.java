package io.github.asgambat.xdcc.irc;

public record IrcConnectionConfig(String host, int port, String nick, boolean secure, int verbosity) {}
