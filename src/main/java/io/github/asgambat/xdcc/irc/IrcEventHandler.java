package io.github.asgambat.xdcc.irc;

import java.util.Set;

/**
 * Callback interface for IRC events relevant to XDCC downloads.
 * Implemented by {@link XdccIrcClient} to react to IRC events without
 * depending on any specific IRC library.
 */
public interface IrcEventHandler {
    void onConnected();
    void onDisconnected();
    void onChannelJoined(String channel, String joinerNick);
    void onNotice(String senderNick, String message);

    /**
     * Called when a CTCP message is received (typically DCC SEND or DCC ACCEPT).
     * @param ctcpContent the CTCP payload WITHOUT the "DCC " prefix,
     *                    e.g. "SEND filename 1234567 6667 12345"
     */
    void onCtcpMessage(String senderNick, String senderHost, String ctcpContent);
    void onWhoisChannels(String nick, Set<String> channels);

    /** Called on IRC numeric replies (e.g. 401 = ERR_NOSUCHNICK). */
    void onNumericReply(int code);
}
