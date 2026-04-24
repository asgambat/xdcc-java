package io.github.asgambat.xdcc.irc;

import io.github.asgambat.xdcc.irc.kitteh.KittehIrcClient;
import io.github.asgambat.xdcc.irc.pircbotx.PircBotXIrcClient;
import jakarta.inject.Singleton;

@Singleton
public class DefaultIrcClientFactory implements IrcClientFactory {

    @Override
    public IrcClient create() {
        return create("pircbotx");
    }

    @Override
    public IrcClient create(String implementation) {
        return switch (implementation.toLowerCase()) {
            case "kitteh" -> new KittehIrcClient();
            case "pircbotx" -> new PircBotXIrcClient();
            default -> throw new IllegalArgumentException("Unknown IRC library: " + implementation
                    + ". Supported: pircbotx, kitteh");
        };
    }
}
