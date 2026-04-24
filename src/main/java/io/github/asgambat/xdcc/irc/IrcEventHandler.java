package io.github.asgambat.xdcc.irc;

import java.util.Set;

public interface IrcEventHandler {
    void onConnected();
    void onDisconnected();
    void onChannelJoined(String channel, String joinerNick);
    void onNotice(String senderNick, String message);
    void onCtcpMessage(String senderNick, String senderHost, String ctcpContent);
    void onWhoisChannels(String nick, Set<String> channels);
    void onNumericReply(int code);
}
