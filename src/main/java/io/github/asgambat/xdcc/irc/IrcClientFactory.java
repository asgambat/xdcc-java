package io.github.asgambat.xdcc.irc;

public interface IrcClientFactory {
    IrcClient create();
    default IrcClient create(String implementation) {
        return create();
    }
}
