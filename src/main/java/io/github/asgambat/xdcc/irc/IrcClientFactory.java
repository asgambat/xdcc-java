package io.github.asgambat.xdcc.irc;

/**
 * Factory that creates the appropriate {@link IrcClient} implementation at runtime.
 * Default is "pircbotx"; also supports "kitteh". Selected via CLI --irc-library flag.
 */
public interface IrcClientFactory {
    IrcClient create();
    default IrcClient create(String implementation) {
        return create();
    }
}
