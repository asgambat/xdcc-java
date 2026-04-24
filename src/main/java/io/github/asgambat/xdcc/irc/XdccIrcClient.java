package io.github.asgambat.xdcc.irc;

import io.github.asgambat.xdcc.domain.IrcServer;
import io.github.asgambat.xdcc.domain.PackResult;
import io.github.asgambat.xdcc.domain.XdccPack;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class XdccIrcClient implements IrcEventHandler {

    private final List<XdccPack> packs;
    private final DownloadOptions opts;
    private final int verbosity;
    private final SecureRandom random = new SecureRandom();

    private final IrcClient ircClient;
    private volatile IrcConnectionConfig connectionConfig;
    private final CountDownLatch connectedLatch = new CountDownLatch(1);
    private volatile boolean connectionFailed = false;
    private volatile XdccError connectionError = null;

    // Per-pack state (reset between packs)
    private volatile int currentPackIndex = 0;
    private volatile CountDownLatch downloadStartedLatch = new CountDownLatch(1);
    private volatile CountDownLatch downloadDoneLatch = new CountDownLatch(1);
    private final AtomicReference<XdccError> downloadErrorRef = new AtomicReference<>();
    private volatile String lastBotNotice = "";
    private final AtomicBoolean messageSent = new AtomicBoolean(false);
    private final Set<String> joinedChannels = ConcurrentHashMap.newKeySet();
    private final Set<String> channelsToJoin = ConcurrentHashMap.newKeySet();
    private volatile boolean resumePending = false;
    private volatile String resumeRemoteIp = "";
    private volatile int resumePort = 0;
    private volatile long resumePosition = 0;

    public XdccIrcClient(IrcClient ircClient, List<XdccPack> packs, DownloadOptions opts, int verbosity) {
        this.ircClient = ircClient;
        this.packs = packs;
        this.opts = opts;
        this.verbosity = verbosity;
    }

    public List<PackResult> downloadAll() {
        List<PackResult> results = new ArrayList<>();
        try {
            connect();
        } catch (XdccError e) {
            for (XdccPack ignored : packs) {
                results.add(PackResult.failure(e, ""));
            }
            return results;
        }

        for (int i = 0; i < packs.size(); i++) {
            if (i > 0) {
                try { Thread.sleep(3000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            PackResult result = downloadPackAtIndex(i, 0);
            results.add(result);
        }

        try { ircClient.shutdown("Bye"); } catch (Exception ignored) {} // uses IrcClient interface
        return results;
    }

    private void connect() throws XdccError {
        IrcServer server = packs.get(0).getServer();
        String resolvedIp = resolveHost(server.address());

        String nick = opts.getUsername();
        if (nick == null || nick.isEmpty()) {
            nick = randomUsername();
        }

        connectionConfig = new IrcConnectionConfig(resolvedIp, server.port(), nick, false, verbosity);

        try {
            ircClient.setEventHandler(this);
            ircClient.connect(connectionConfig);

            boolean connected = connectedLatch.await(opts.getConnectTimeout(), TimeUnit.SECONDS);
            if (!connected || connectionFailed) {
                throw connectionError != null ? connectionError :
                        XdccError.serverUnreachable("Connection timed out to " + server.address());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw XdccError.serverUnreachable("Interrupted while connecting");
        } catch (XdccError e) {
            throw e;
        } catch (Exception e) {
            throw XdccError.serverUnreachable("Failed to create IRC client: " + e.getMessage(), e);
        }
    }

    private PackResult downloadPackAtIndex(int index, int retryCount) {
        currentPackIndex = index;
        resetForPack();

        XdccPack pack = packs.get(index);

        // Channel join delay only for first pack
        if (index == 0) {
            int delay = opts.getChannelJoinDelay();
            if (delay < 0) {
                delay = 5 + random.nextInt(6);
            }
            if (delay > 0) {
                try { Thread.sleep(delay * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        }

        if (verbosity >= 1) {
            System.out.println("Sending WHOIS for " + pack.getBot());
        }
        ircClient.sendRawLine("WHOIS " + pack.getBot());

        try {
            PackResult result = waitForCurrentPack(pack);
            if (result.error() != null && result.error().is(XdccError.Kind.PACK_ALREADY_REQUESTED)) {
                System.out.println("Pack already requested, waiting 60s...");
                try { Thread.sleep(60000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                return downloadPackAtIndex(index, retryCount);
            }
            if (result.error() != null &&
                    (result.error().is(XdccError.Kind.TIMEOUT) || result.error().is(XdccError.Kind.DOWNLOAD_FAILED))
                    && retryCount < 3) {
                System.out.println("Retrying pack #" + pack.getPackNumber() + " (attempt " + (retryCount + 1) + ")...");
                try { reconnect(); } catch (Exception ignored) {}
                return downloadPackAtIndex(index, retryCount + 1);
            }
            return result;
        } catch (Exception e) {
            return PackResult.failure(XdccError.downloadFailed(e.getMessage()), lastBotNotice);
        }
    }

    private PackResult waitForCurrentPack(XdccPack pack) throws InterruptedException {
        int phase1Timeout = opts.getConnectTimeout() + opts.getWaitTime() + 30;
        boolean transferStarted = downloadStartedLatch.await(phase1Timeout, TimeUnit.SECONDS);
        if (!transferStarted) {
            XdccError err = downloadErrorRef.get();
            return PackResult.failure(err != null ? err : XdccError.TIMEOUT, lastBotNotice);
        }

        boolean done = downloadDoneLatch.await(24, TimeUnit.HOURS);
        XdccError err = downloadErrorRef.get();
        if (err != null) {
            return PackResult.failure(err, lastBotNotice);
        }
        if (!done) {
            return PackResult.failure(XdccError.TIMEOUT, lastBotNotice);
        }
        return PackResult.success(pack.getFilepath());
    }

    private void resetForPack() {
        downloadStartedLatch = new CountDownLatch(1);
        downloadDoneLatch = new CountDownLatch(1);
        downloadErrorRef.set(null);
        messageSent.set(false);
        channelsToJoin.clear();
        resumePending = false;
        lastBotNotice = "";
    }

    private void reconnect() throws InterruptedException {
        try { ircClient.shutdown("Reconnecting"); } catch (Exception ignored) {}
        Thread.sleep(3000);
        try { ircClient.connect(connectionConfig); } catch (XdccError ignored) {}
    }

    private void sendXdccRequest(XdccPack pack) {
        if (!messageSent.compareAndSet(false, true)) return;

        int waitTime = opts.getWaitTime();
        if (waitTime > 0) {
            Thread.ofVirtual().start(() -> {
                try { Thread.sleep(waitTime * 1000L); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                if (verbosity >= 0) System.out.println("Requesting " + pack.getRequestMessage(true));
                ircClient.sendMessage(pack.getBot(), pack.getRequestMessage(false));
            });
        } else {
            if (verbosity >= 0) System.out.println("Requesting " + pack.getRequestMessage(true));
            ircClient.sendMessage(pack.getBot(), pack.getRequestMessage(false));
        }
    }

    private void handleDccSend(String senderNick, String senderHost, String dccParams) {
        // DCC SEND <filename> <ip> <port> <size>
        String[] parts = splitDCC(dccParams.substring("SEND ".length()));
        if (parts.length < 4) return;

        String filename = parts[0];
        if (filename.startsWith("\"") && filename.endsWith("\"")) {
            filename = filename.substring(1, filename.length() - 1);
        }
        String ipStr = parts[1];
        int port;
        long filesize;
        try {
            port = Integer.parseInt(parts[2]);
            filesize = Long.parseLong(parts[3]);
        } catch (NumberFormatException e) {
            return;
        }

        // Convert numeric IP to dotted notation
        String remoteIp;
        if (ipStr.equals("0") || ipStr.equals("0.0.0.0")) {
            remoteIp = senderHost;
        } else {
            try {
                long ipLong = Long.parseLong(ipStr);
                remoteIp = ((ipLong >> 24) & 0xFF) + "." + ((ipLong >> 16) & 0xFF) + "." +
                           ((ipLong >> 8) & 0xFF) + "." + (ipLong & 0xFF);
            } catch (NumberFormatException e) {
                remoteIp = ipStr;
            }
        }

        XdccPack pack = packs.get(currentPackIndex);
        pack.setOriginalFilename(filename);
        if (pack.getFilename() == null || pack.getFilename().isEmpty()) {
            pack.setFilename(filename, true);
        } else {
            pack.setFilename(filename, false);
        }
        pack.setSize(filesize);

        // Check if already downloaded
        File f = new File(pack.getFilepath());
        if (f.exists() && f.length() >= filesize) {
            downloadErrorRef.set(XdccError.ALREADY_DOWNLOADED);
            downloadStartedLatch.countDown();
            downloadDoneLatch.countDown();
            return;
        }

        // Resume if partial file
        long resumePos = 0;
        if (f.exists() && f.length() > 0 && f.length() < filesize) {
            resumePos = f.length();
            resumePending = true;
            resumeRemoteIp = remoteIp;
            resumePort = port;
            resumePosition = resumePos;
            ircClient.sendRawLine("PRIVMSG " + senderNick + " :\u0001DCC RESUME \"" + filename + "\" " + port + " " + resumePos + "\u0001");
            return;
        }

        startDccTransfer(pack, remoteIp, port, filesize, 0);
    }

    private void handleDccAccept(String dccParams) {
        if (!resumePending) return;
        // DCC ACCEPT <filename> <port> <position>
        String[] parts = splitDCC(dccParams.substring("ACCEPT ".length()));
        if (parts.length < 3) return;
        long acceptedPos;
        try {
            acceptedPos = Long.parseLong(parts[2]);
        } catch (NumberFormatException e) {
            return;
        }
        XdccPack pack = packs.get(currentPackIndex);
        startDccTransfer(pack, resumeRemoteIp, resumePort, pack.getSize(), acceptedPos);
    }

    private void startDccTransfer(XdccPack pack, String remoteIp, int port, long filesize, long resumePos) {
        DccTransfer transfer = new DccTransfer(pack, remoteIp, port, filesize, resumePos, opts, verbosity);
        transfer.start();

        Thread.ofVirtual().start(() -> {
            try {
                transfer.getStartedLatch().await();
                XdccError startErr = transfer.getError();
                if (startErr != null) {
                    downloadErrorRef.set(startErr);
                    downloadStartedLatch.countDown();
                    downloadDoneLatch.countDown();
                    return;
                }
                downloadStartedLatch.countDown();

                transfer.getDoneLatch().await();
                XdccError doneErr = transfer.getError();
                if (doneErr != null) {
                    downloadErrorRef.set(doneErr);
                }
                downloadDoneLatch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    /** Strips user mode prefixes (@, +, %, ~, &) from WHOIS channel names. */
    private static String stripModePrefix(String channelName) {
        // WHOIS RPL_WHOISCHANNELS prefixes channels with user status: @=op, +=voice, %=halfop, ~=owner, &=admin
        int i = 0;
        while (i < channelName.length() && "~&@%+!".indexOf(channelName.charAt(i)) >= 0
                && i + 1 < channelName.length() && channelName.charAt(i + 1) != ' ') {
            // Only strip if there's a channel prefix (#) after the mode chars
            if (channelName.charAt(i + 1) == '#') {
                return channelName.substring(i + 1);
            }
            i++;
        }
        return channelName;
    }

    private String resolveHost(String host) throws XdccError {
        // Attempt 1: system DNS
        try {
            InetAddress[] addrs = InetAddress.getAllByName(host);
            for (InetAddress addr : addrs) {
                String ip = addr.getHostAddress();
                if (!ip.equals("0.0.0.0") && !ip.equals("::") && !ip.isBlank()) {
                    if (verbosity >= 2) System.out.println("[dns] Resolved " + host + " → " + ip + " (system)");
                    return ip;
                }
            }
            if (verbosity >= 0) System.err.println("[dns] System DNS returned blocked address for " + host + " — trying fallback");
        } catch (UnknownHostException ignored) {
            if (verbosity >= 1) System.err.println("[dns] System DNS failed for " + host + " — trying fallback");
        }

        // Attempt 2: raw UDP DNS query to fallback resolver (no external library)
        String dnsServer = opts.getDnsServer();
        if (dnsServer == null || dnsServer.isEmpty()) dnsServer = "8.8.8.8:53";
        String dnsHost = dnsServer.contains(":") ? dnsServer.substring(0, dnsServer.lastIndexOf(':')) : dnsServer;
        int dnsPort = 53;
        try { dnsPort = Integer.parseInt(dnsServer.substring(dnsServer.lastIndexOf(':') + 1)); }
        catch (NumberFormatException ignored) {}

        try {
            String ip = rawDnsLookup(host, dnsHost, dnsPort);
            if (ip != null) {
                if (verbosity >= 0) System.out.println("[dns] Resolved " + host + " → " + ip + " via " + dnsServer);
                return ip;
            }
        } catch (Exception e) {
            if (verbosity >= 1) System.err.println("[dns] Fallback DNS failed: " + e.getMessage());
        }

        throw XdccError.serverUnreachable("Cannot resolve hostname: " + host + ". Use --server with a direct IP.");
    }

    /**
     * Sends a minimal DNS A-record query over UDP and returns the first IPv4 address found.
     * Implemented using raw DatagramSocket — no external DNS library required.
     */
    private static String rawDnsLookup(String hostname, String dnsHost, int dnsPort) throws IOException {
        byte[] query = buildDnsQuery(hostname);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(5000);
            InetAddress dnsAddr = InetAddress.getByName(dnsHost);
            socket.send(new DatagramPacket(query, query.length, dnsAddr, dnsPort));
            byte[] buf = new byte[512];
            DatagramPacket resp = new DatagramPacket(buf, buf.length);
            socket.receive(resp);
            return parseFirstARecord(buf, resp.getLength());
        }
    }

    /** Builds a minimal DNS query packet for an A (IPv4) record. */
    private static byte[] buildDnsQuery(String hostname) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeShort(0x1A2B);  // transaction ID
        dos.writeShort(0x0100);  // flags: standard query, recursion desired
        dos.writeShort(1);       // QDCOUNT: 1 question
        dos.writeShort(0);       // ANCOUNT
        dos.writeShort(0);       // NSCOUNT
        dos.writeShort(0);       // ARCOUNT
        // Encode QNAME as length-prefixed labels
        for (String label : hostname.split("\\.")) {
            byte[] lb = label.getBytes(StandardCharsets.US_ASCII);
            dos.writeByte(lb.length);
            dos.write(lb);
        }
        dos.writeByte(0);        // root label (end of QNAME)
        dos.writeShort(1);       // QTYPE: A
        dos.writeShort(1);       // QCLASS: IN
        return baos.toByteArray();
    }

    /** Parses the first A record from a DNS response packet. Returns null if none found. */
    private static String parseFirstARecord(byte[] response, int length) throws IOException {
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(response, 0, length));
        dis.skipBytes(4);                           // ID + flags
        int qdcount = dis.readUnsignedShort();
        int ancount = dis.readUnsignedShort();
        dis.skipBytes(4);                           // NSCOUNT + ARCOUNT
        // Skip question section
        for (int i = 0; i < qdcount; i++) {
            skipDnsName(response, dis);
            dis.skipBytes(4);                       // QTYPE + QCLASS
        }
        // Parse answer section
        for (int i = 0; i < ancount; i++) {
            skipDnsName(response, dis);
            int type     = dis.readUnsignedShort(); // TYPE
            dis.skipBytes(6);                       // CLASS + TTL
            int rdlength = dis.readUnsignedShort();
            if (type == 1 && rdlength == 4) {       // A record
                byte[] addr = new byte[4];
                dis.readFully(addr);
                return (addr[0] & 0xFF) + "." + (addr[1] & 0xFF) + "." + (addr[2] & 0xFF) + "." + (addr[3] & 0xFF);
            }
            dis.skipBytes(rdlength);
        }
        return null;
    }

    /** Skips a DNS name field (handles label compression pointers). */
    private static void skipDnsName(byte[] response, DataInputStream dis) throws IOException {
        int b;
        while ((b = dis.readUnsignedByte()) != 0) {
            if ((b & 0xC0) == 0xC0) {              // compression pointer: 2 bytes total
                dis.skipBytes(1);
                return;
            }
            dis.skipBytes(b);                       // skip label of length b
        }
    }

    private String randomUsername() {
        String[] firstNames = {"Alex", "Chris", "Jordan", "Morgan", "Casey", "Taylor", "Jamie", "Riley"};
        String[] lastNames = {"Smith", "Jones", "Brown", "Wilson", "Davis", "Miller", "Moore"};
        String first = firstNames[random.nextInt(firstNames.length)];
        String last = lastNames[random.nextInt(lastNames.length)];
        return first + last + randomSuffix(3);
    }

    private String randomSuffix(int n) {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static String[] splitDCC(String s) {
        List<String> parts = new ArrayList<>();
        boolean inQuote = false;
        StringBuilder current = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '"') {
                inQuote = !inQuote;
                current.append(c);
            } else if (c == ' ' && !inQuote) {
                if (current.length() > 0) {
                    parts.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) parts.add(current.toString());
        return parts.toArray(new String[0]);
    }

    // --- IrcEventHandler implementation ---

    @Override
    public void onConnected() {
        if (verbosity >= 1) System.out.println("Connected to IRC server");
        connectedLatch.countDown();
    }

    @Override
    public void onDisconnected() {
        if (verbosity >= 1) System.out.println("Disconnected from IRC server");
        if (connectedLatch.getCount() > 0) {
            connectionFailed = true;
            connectionError = XdccError.serverUnreachable("Connection ended before negotiation complete");
            connectedLatch.countDown();
        }
        if (downloadErrorRef.get() == null) {
            downloadErrorRef.set(XdccError.UNRECOVERABLE);
        }
        downloadStartedLatch.countDown();
        downloadDoneLatch.countDown();
    }

    @Override
    public void onChannelJoined(String channel, String joinerNick) {
        if (!joinerNick.equals(ircClient.getNick())) return;

        joinedChannels.add(channel.toLowerCase());
        if (verbosity >= 1) System.out.println("Joined channel: " + channel);

        // Send XDCC request once all needed channels are joined
        if (!channelsToJoin.isEmpty() &&
                channelsToJoin.stream().allMatch(c -> joinedChannels.contains(c.toLowerCase()))) {
            XdccPack pack = packs.get(currentPackIndex);
            sendXdccRequest(pack);
        }
    }

    @Override
    public void onNotice(String senderNick, String message) {
        lastBotNotice = message;

        if (verbosity >= 0) {
            System.out.println("[" + senderNick + "] " + message);
        }

        String lc = message.toLowerCase();
        if (lc.contains("you already requested") || lc.contains("richiesto questo pack")) {
            downloadErrorRef.set(XdccError.PACK_ALREADY_REQUESTED);
            downloadStartedLatch.countDown();
            downloadDoneLatch.countDown();
        } else if (lc.contains("denied") || lc.contains("slot") || lc.contains("negato") || lc.contains("errato")) {
            downloadErrorRef.set(XdccError.botDenied(message));
            downloadStartedLatch.countDown();
            downloadDoneLatch.countDown();
        }
    }

    @Override
    public void onCtcpMessage(String senderNick, String senderHost, String ctcpContent) {
        if (ctcpContent.startsWith("SEND ")) {
            handleDccSend(senderNick, senderHost, ctcpContent);
        } else if (ctcpContent.startsWith("ACCEPT ")) {
            handleDccAccept(ctcpContent);
        }
    }

    @Override
    public void onWhoisChannels(String nick, Set<String> channels) {
        XdccPack pack = packs.get(currentPackIndex);
        if (!nick.equalsIgnoreCase(pack.getBot())) return;

        if (!channels.isEmpty()) {
            Set<String> cleanChannels = new HashSet<>();
            for (String raw : channels) {
                cleanChannels.add(stripModePrefix(raw));
            }
            channelsToJoin.addAll(cleanChannels);
            for (String ch : channelsToJoin) {
                if (!joinedChannels.contains(ch.toLowerCase())) {
                    if (verbosity >= 1) System.out.println("Joining channel: " + ch);
                    ircClient.joinChannel(ch);
                }
            }
            if (channelsToJoin.stream().allMatch(c -> joinedChannels.contains(c.toLowerCase()))) {
                sendXdccRequest(pack);
            }
        } else {
            String fallback = opts.getFallbackChannel();
            if (fallback != null && !fallback.isEmpty()) {
                channelsToJoin.add(fallback);
                if (!joinedChannels.contains(fallback.toLowerCase())) {
                    ircClient.joinChannel(fallback);
                } else {
                    sendXdccRequest(pack);
                }
            } else {
                sendXdccRequest(pack);
            }
        }
    }

    @Override
    public void onNumericReply(int code) {
        if (code == 401) {
            // ERR_NOSUCHNICK
            downloadErrorRef.set(XdccError.BOT_NOT_FOUND);
            downloadStartedLatch.countDown();
            downloadDoneLatch.countDown();
        }
    }
}
