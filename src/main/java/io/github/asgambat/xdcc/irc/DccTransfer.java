package io.github.asgambat.xdcc.irc;

import io.github.asgambat.xdcc.domain.XdccPack;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages a DCC file transfer over a TCP socket.
 *
 * <p>Runs the actual TCP receive loop in a virtual thread. Spawns additional
 * virtual threads for stall detection and progress display.
 *
 * <p>DCC protocol requires sending ACK bytes after each chunk received:
 * 4-byte big-endian for files ≤ 4GB, 8-byte for larger files.
 */
public class DccTransfer {

    private final XdccPack pack;
    private final String remoteIp;
    private final int remotePort;
    private final long remoteFileSize;
    private final long resumePosition;
    private final DownloadOptions opts;
    private final int verbosity;

    private volatile Socket socket;
    private volatile long progress;
    private volatile long lastActivityNano = System.nanoTime();
    private volatile Instant startTime;
    private volatile boolean done = false;

    private final CountDownLatch startedLatch = new CountDownLatch(1);
    private final CountDownLatch doneLatch = new CountDownLatch(1);
    private final AtomicReference<XdccError> errorRef = new AtomicReference<>();

    public DccTransfer(XdccPack pack, String remoteIp, int remotePort,
                       long remoteFileSize, long resumePosition,
                       DownloadOptions opts, int verbosity) {
        this.pack = pack;
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.remoteFileSize = remoteFileSize;
        this.resumePosition = resumePosition;
        this.opts = opts;
        this.verbosity = verbosity;
        this.progress = resumePosition;
    }

    public void start() {
        Thread.ofVirtual().start(this::run);
    }

    public CountDownLatch getStartedLatch() { return startedLatch; }
    public CountDownLatch getDoneLatch() { return doneLatch; }
    public XdccError getError() { return errorRef.get(); }

    private void run() {
        try {
            socket = new Socket(remoteIp, remotePort);
            socket.setSoTimeout(0); // blocking read
        } catch (IOException e) {
            errorRef.set(XdccError.serverUnreachable("Cannot connect to DCC: " + e.getMessage(), e));
            startedLatch.countDown();
            doneLatch.countDown();
            return;
        }

        File file = new File(pack.getFilepath());
        // Ensure parent dirs exist
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();

        // Atomically check if file is already fully downloaded
        if (file.exists() && file.length() >= remoteFileSize) {
            errorRef.set(XdccError.ALREADY_DOWNLOADED);
            startedLatch.countDown();
            doneLatch.countDown();
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        FileOutputStream fos;
        try {
            fos = new FileOutputStream(file, resumePosition > 0);
        } catch (IOException e) {
            errorRef.set(XdccError.downloadFailed("Cannot open file: " + e.getMessage()));
            startedLatch.countDown();
            doneLatch.countDown();
            try { socket.close(); } catch (IOException ignored) {}
            return;
        }

        startTime = Instant.now();
        startedLatch.countDown();

        // Start stall watcher if enabled
        if (opts.getStallTimeout() > 0) {
            Thread.ofVirtual().start(this::stallWatcher);
        }

        // Start progress printer if verbosity >= 0
        if (verbosity >= 0) {
            Thread.ofVirtual().start(this::progressPrinter);
        }

        // Receive data
        try {
            receiveData(fos);
        } catch (IOException e) {
            if (!done) {
                errorRef.set(XdccError.downloadFailed("IO error during transfer: " + e.getMessage()));
            }
        } finally {
            try { fos.close(); } catch (IOException ignored) {}
            try { socket.close(); } catch (IOException ignored) {}
            doneLatch.countDown();
        }
    }

    private void receiveData(FileOutputStream fos) throws IOException {
        InputStream in = socket.getInputStream();
        OutputStream out = socket.getOutputStream();

        byte[] buf = new byte[65536];
        int bytesRead;
        long throttle = opts.getThrottleBytes();
        long chunkStart = System.nanoTime();
        long chunkBytes = 0;

        while ((bytesRead = in.read(buf)) > 0) {
            fos.write(buf, 0, bytesRead);
            progress += bytesRead;
            lastActivityNano = System.nanoTime();
            chunkBytes += bytesRead;

            // Send ACK
            sendAck(out, progress);

            // Throttle
            if (throttle > 0) {
                chunkStart = throttle(throttle, chunkStart, chunkBytes);
                chunkBytes = 0;
            }
        }

        if (progress >= remoteFileSize) {
            done = true;
            if (verbosity >= 0) {
                printFinalStats();
            }
        } else {
            errorRef.set(XdccError.downloadFailed("Incomplete download: got " + progress + "/" + remoteFileSize));
        }
    }

    /** Token-bucket style throttle: sleeps if data arrived faster than the configured limit. */
    private long throttle(long throttleBytes, long chunkStart, long chunkBytes) {
        long elapsed = System.nanoTime() - chunkStart;
        long expectedNs = (long) ((double) chunkBytes / throttleBytes * 1_000_000_000L);
        if (elapsed < expectedNs) {
            try {
                Thread.sleep((expectedNs - elapsed) / 1_000_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return System.nanoTime();
    }

    /**
     * Sends a DCC acknowledgment: the total bytes received so far, as a big-endian integer.
     * Per DCC protocol: 4 bytes if total ≤ 4GB, 8 bytes otherwise.
     */
    private void sendAck(OutputStream out, long progressVal) throws IOException {
        byte[] ack;
        if (progressVal <= 0xFFFFFFFFL) {
            ack = new byte[4];
            ack[0] = (byte) ((progressVal >> 24) & 0xFF);
            ack[1] = (byte) ((progressVal >> 16) & 0xFF);
            ack[2] = (byte) ((progressVal >> 8) & 0xFF);
            ack[3] = (byte) (progressVal & 0xFF);
        } else {
            ack = ByteBuffer.allocate(8).putLong(progressVal).array();
        }
        out.write(ack);
        out.flush();
    }

    /** Monitors transfer activity; closes socket if no data received for stallTimeout seconds. */
    private void stallWatcher() {
        int stallTimeout = opts.getStallTimeout();
        while (!done && errorRef.get() == null) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            long idleSec = (System.nanoTime() - lastActivityNano) / 1_000_000_000L;
            if (idleSec >= stallTimeout) {
                errorRef.set(XdccError.downloadFailed("Stall timeout after " + idleSec + "s of inactivity"));
                try { socket.close(); } catch (IOException ignored) {}
                return;
            }
        }
    }

    private void progressPrinter() {
        while (!done && errorRef.get() == null) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!done && startTime != null) {
                long elapsed = java.time.Duration.between(startTime, Instant.now()).toSeconds();
                double speed = elapsed > 0 ? (double) (progress - resumePosition) / elapsed : 0;
                int percent = remoteFileSize > 0 ? (int) (progress * 100 / remoteFileSize) : 0;
                System.out.printf("\r  %s  %d%%  %s  %s      ",
                        pack.getFilename(), percent,
                        io.github.asgambat.xdcc.domain.XdccPack.humanReadableBytes(progress),
                        formatSpeed(speed));
            }
        }
    }

    private void printFinalStats() {
        long elapsed = java.time.Duration.between(startTime, Instant.now()).toSeconds();
        double speed = elapsed > 0 ? (double) (progress - resumePosition) / elapsed : 0;
        System.out.printf("%nDone: %s [%s] in %s (avg %s)%n",
                pack.getFilename(),
                io.github.asgambat.xdcc.domain.XdccPack.humanReadableBytes(progress),
                formatDuration(elapsed),
                formatSpeed(speed));
    }

    private static String formatSpeed(double bytesPerSec) {
        if (bytesPerSec < 1024 * 1024) {
            return String.format("%.1f KB/s", bytesPerSec / 1024);
        }
        return String.format("%.2f MB/s", bytesPerSec / (1024 * 1024));
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60) return seconds + "s";
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
