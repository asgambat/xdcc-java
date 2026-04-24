package io.github.asgambat.xdcc.irc.kitteh;

import io.github.asgambat.xdcc.irc.IrcClient;
import io.github.asgambat.xdcc.irc.IrcClientFactory;

public class KittehIrcClientFactory implements IrcClientFactory {

    @Override
    public IrcClient create() {
        return new KittehIrcClient();
    }
}
