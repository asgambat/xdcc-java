package io.github.asgambat.xdcc.irc.pircbotx;

import io.github.asgambat.xdcc.irc.IrcClient;
import io.github.asgambat.xdcc.irc.IrcConnectionConfig;
import io.github.asgambat.xdcc.irc.IrcEventHandler;
import io.github.asgambat.xdcc.irc.XdccError;
import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UserHostmask;
import org.pircbotx.User;
import org.pircbotx.dcc.DccHandler;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.*;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * PircBotX-based IRC client implementation.
 *
 * <p>Key challenge: PircBotX handles DCC CTCP messages internally via its {@code DccHandler},
 * which would open its own DCC connections. We need to intercept DCC SEND/ACCEPT messages
 * before PircBotX processes them, so our own {@link XdccIrcClient} can manage the DCC transfer.
 *
 * <p>Solution: inject a {@link CustomBotFactory} that produces an {@link InterceptingDccHandler},
 * which overrides {@code processDcc()} to capture SEND/ACCEPT and delegate to our
 * {@link IrcEventHandler#onCtcpMessage}.
 *
 * <p>Note: {@code PircBotX.startBot()} is blocking, so it runs in a virtual thread.
 */
public class PircBotXIrcClient implements IrcClient {
    private volatile PircBotX bot;
    private volatile IrcEventHandler handler;
    private final CountDownLatch startedLatch = new CountDownLatch(1);

    @Override
    public void setEventHandler(IrcEventHandler handler) {
        this.handler = handler;
    }

    @Override
    public void connect(IrcConnectionConfig config) throws XdccError {
        try {
            Configuration configuration = new Configuration.Builder()
                    .setName(config.nick())
                    .setLogin(config.nick())
                    .setRealName(config.nick())
                    .addServer(config.host(), config.port())
                    .setAutoReconnect(false)
                    .setAutoNickChange(true)
                    .addListener(new EventBridge())
                    .setBotFactory(new CustomBotFactory())
                    .setShutdownHookEnabled(false)
                    .buildConfiguration();

            bot = new PircBotX(configuration);

            // startBot() is blocking, run in virtual thread
            Thread.ofVirtual().name("pircbotx-connect").start(() -> {
                try {
                    startedLatch.countDown();
                    bot.startBot();
                } catch (Exception e) {
                    startedLatch.countDown();
                    if (handler != null) handler.onDisconnected();
                }
            });

            startedLatch.await(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            throw XdccError.serverUnreachable("Failed to create PircBotX client: " + e.getMessage(), e);
        }
    }

    @Override
    public void sendRawLine(String line) {
        if (bot != null) bot.sendRaw().rawLine(line);
    }

    @Override
    public void sendMessage(String target, String message) {
        if (bot != null) bot.sendIRC().message(target, message);
    }

    @Override
    public void joinChannel(String channel) {
        if (bot != null) {
            try {
                bot.sendIRC().joinChannel(channel);
            } catch (Exception e) {
                System.out.println("Skipping invalid channel: " + channel);
            }
        }
    }

    @Override
    public String getNick() {
        return bot != null ? bot.getNick() : "";
    }

    @Override
    public void shutdown(String message) {
        if (bot != null) {
            try {
                bot.sendIRC().quitServer(message);
            } catch (Exception ignored) {}
            try {
                bot.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * Intercepts DCC CTCP messages (SEND, ACCEPT) before PircBotX's default handler
     * opens its own DCC connections. Passes the parsed content to our IrcEventHandler.
     * Non-DCC-SEND/ACCEPT messages (e.g. DCC CHAT) fall through to PircBotX's default behavior.
     */
    private class InterceptingDccHandler extends DccHandler {
        public InterceptingDccHandler(PircBotX bot) {
            super(bot);
        }

        @Override
        public boolean processDcc(UserHostmask userHostmask, User user, String request) throws IOException {
            if (request.startsWith("DCC ") && handler != null) {
                String content = request.substring(4); // strip "DCC " prefix
                String nick = userHostmask.getNick();
                String host = userHostmask.getHostname() != null ? userHostmask.getHostname() : "";

                if (content.startsWith("SEND ") || content.startsWith("ACCEPT ")) {
                    handler.onCtcpMessage(nick, host, content);
                    return true;
                }
            }
            return super.processDcc(userHostmask, user, request);
        }
    }

    /** Replaces PircBotX's default BotFactory to inject our InterceptingDccHandler. */
    private class CustomBotFactory extends Configuration.BotFactory {
        @Override
        public DccHandler createDccHandler(PircBotX bot) {
            return new InterceptingDccHandler(bot);
        }
    }

    /** Bridges PircBotX library events to our library-agnostic IrcEventHandler. */
    private class EventBridge extends ListenerAdapter {
        @Override
        public void onConnect(ConnectEvent event) {
            if (handler != null) handler.onConnected();
        }

        @Override
        public void onDisconnect(DisconnectEvent event) {
            if (handler != null) handler.onDisconnected();
        }

        @Override
        public void onJoin(JoinEvent event) {
            if (handler != null) {
                String channelName = event.getChannel().getName();
                String nick = event.getUser() != null ? event.getUser().getNick() : event.getUserHostmask().getNick();
                handler.onChannelJoined(channelName, nick);
            }
        }

        @Override
        public void onNotice(NoticeEvent event) {
            if (handler != null) {
                String nick = event.getUserHostmask().getNick();
                handler.onNotice(nick, event.getNotice());
            }
        }

        @Override
        public void onWhois(WhoisEvent event) {
            if (handler != null) {
                Set<String> channels = new HashSet<>(event.getChannels());
                handler.onWhoisChannels(event.getNick(), channels);
            }
        }

        @Override
        public void onServerResponse(ServerResponseEvent event) {
            if (handler != null) {
                handler.onNumericReply(event.getCode());
            }
        }
    }
}
