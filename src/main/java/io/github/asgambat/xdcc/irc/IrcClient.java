package io.github.asgambat.xdcc.irc;

public interface IrcClient {
    void connect(IrcConnectionConfig config) throws XdccError;
    void sendRawLine(String line);
    void sendMessage(String target, String message);
    void joinChannel(String channel);
    String getNick();
    void shutdown(String message);
    void setEventHandler(IrcEventHandler handler);
}
