package io.github.asgambat.xdcc.irc.kitteh;

import io.github.asgambat.xdcc.irc.IrcClient;
import io.github.asgambat.xdcc.irc.IrcConnectionConfig;
import io.github.asgambat.xdcc.irc.IrcEventHandler;
import io.github.asgambat.xdcc.irc.XdccError;
import net.engio.mbassy.listener.Handler;
import org.kitteh.irc.client.library.Client;
import org.kitteh.irc.client.library.event.channel.ChannelJoinEvent;
import org.kitteh.irc.client.library.event.client.ClientNegotiationCompleteEvent;
import org.kitteh.irc.client.library.event.client.ClientReceiveNumericEvent;
import org.kitteh.irc.client.library.event.connection.ClientConnectionEndedEvent;
import org.kitteh.irc.client.library.event.user.PrivateCtcpQueryEvent;
import org.kitteh.irc.client.library.event.user.PrivateNoticeEvent;
import org.kitteh.irc.client.library.event.user.WhoisEvent;
import org.kitteh.irc.client.library.exception.KittehNagException;

import java.util.Set;

/**
 * KittehIRCClientLib-based IRC client implementation.
 *
 * <p>Kitteh uses an event bus (MBassador) — events are received via {@code @Handler} annotations
 * in the inner {@link KittehEventBridge} class. CTCP messages arrive as
 * {@link PrivateCtcpQueryEvent} with the full content including "DCC " prefix, which we strip
 * before forwarding to {@link IrcEventHandler#onCtcpMessage}.
 *
 * <p>Quirks handled:
 * <ul>
 *   <li>{@code .secure(false)} — Kitteh defaults to SSL/TLS; most XDCC servers use plaintext</li>
 *   <li>{@link KittehNagException} — thrown when connecting without TLS; suppressed via custom exception handler</li>
 *   <li>{@code addChannel()} validates channel names — WHOIS may return names with mode prefixes</li>
 * </ul>
 */
public class KittehIrcClient implements IrcClient {

    private volatile Client client;
    private volatile IrcEventHandler handler;

    @Override
    public void setEventHandler(IrcEventHandler handler) {
        this.handler = handler;
    }

    @Override
    public void connect(IrcConnectionConfig config) throws XdccError {
        try {
            client = Client.builder()
                    .nick(config.nick())
                    .user(config.nick())
                    .realName(config.nick())
                    .server()
                        .host(config.host())
                        .port(config.port())
                        .secure(config.secure())
                        .then()
                    .listeners()
                        .exception(e -> {
                            if (!(e instanceof KittehNagException)) {
                                if (config.verbosity() >= 2) e.printStackTrace();
                            }
                        })
                        .then()
                    .build();

            client.getEventManager().registerEventListener(new KittehEventBridge());
            client.connect();
        } catch (Exception e) {
            throw XdccError.serverUnreachable("Failed to create IRC client: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendRawLine(String line) {
        client.sendRawLine(line);
    }

    @Override
    public void sendMessage(String target, String message) {
        client.sendMessage(target, message);
    }

    @Override
    public void joinChannel(String channel) {
        try {
            client.addChannel(channel);
        } catch (IllegalArgumentException e) {
            // Skip invalid channel names gracefully
            System.out.println("Skipping invalid channel: " + channel);
        }
    }

    @Override
    public String getNick() {
        return client.getNick();
    }

    @Override
    public void shutdown(String message) {
        client.shutdown(message);
    }

    /** Bridges Kitteh library events to our library-agnostic IrcEventHandler. */
    private class KittehEventBridge {

        @Handler
        public void onNegotiationComplete(ClientNegotiationCompleteEvent event) {
            if (handler != null) handler.onConnected();
        }

        @Handler
        public void onConnectionEnded(ClientConnectionEndedEvent event) {
            event.setAttemptReconnect(false);
            if (handler != null) handler.onDisconnected();
        }

        @Handler
        public void onChannelJoin(ChannelJoinEvent event) {
            if (handler != null) {
                handler.onChannelJoined(event.getChannel().getName(), event.getUser().getNick());
            }
        }

        @Handler
        public void onPrivateNotice(PrivateNoticeEvent event) {
            if (handler != null) {
                handler.onNotice(event.getActor().getNick(), event.getMessage());
            }
        }

        /** CTCP query handler — intercepts DCC SEND/ACCEPT, strips "DCC " prefix for the handler. */
        @Handler
        public void onPrivateCtcpQuery(PrivateCtcpQueryEvent event) {
            String msg = event.getMessage();
            if (msg.startsWith("DCC ")) {
                if (handler != null) {
                    handler.onCtcpMessage(
                            event.getActor().getNick(),
                            event.getActor().getHost(),
                            msg.substring("DCC ".length()));
                }
            }
        }

        @Handler
        public void onWhois(WhoisEvent event) {
            if (handler != null) {
                Set<String> channels = event.getWhoisData().getChannels();
                handler.onWhoisChannels(event.getWhoisData().getNick(), channels);
            }
        }

        @Handler
        public void onNumeric(ClientReceiveNumericEvent event) {
            if (handler != null) {
                handler.onNumericReply(event.getNumeric());
            }
        }
    }
}
