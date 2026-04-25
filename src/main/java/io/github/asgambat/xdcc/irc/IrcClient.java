package io.github.asgambat.xdcc.irc;

/**
 * Abstraction over an IRC client connection.
 * Implementations (PircBotX, Kitteh) wrap library-specific logic behind this interface,
 * allowing the XDCC download logic in {@link XdccIrcClient} to remain library-agnostic.
 */
public interface IrcClient {
    void connect(IrcConnectionConfig config) throws XdccError;
    void sendRawLine(String line);
    void sendMessage(String target, String message);
    void joinChannel(String channel);
    String getNick();
    void shutdown(String message);
    void setEventHandler(IrcEventHandler handler);
}
