package io.github.asgambat.xdcc.downloader;

import io.github.asgambat.xdcc.domain.PackResult;
import io.github.asgambat.xdcc.domain.XdccPack;
import io.github.asgambat.xdcc.irc.DownloadOptions;
import io.github.asgambat.xdcc.irc.IrcClient;
import io.github.asgambat.xdcc.irc.IrcClientFactory;
import io.github.asgambat.xdcc.irc.XdccError;
import io.github.asgambat.xdcc.irc.XdccIrcClient;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

@Singleton
public class XdccDownloader {

    private final IrcClientFactory ircClientFactory;

    public XdccDownloader(IrcClientFactory ircClientFactory) {
        this.ircClientFactory = ircClientFactory;
    }

    public void downloadPacks(List<XdccPack> packs, DownloadOptions opts) {
        List<List<XdccPack>> groups = groupByServer(packs);

        for (List<XdccPack> group : groups) {
            IrcClient ircClient = ircClientFactory.create(opts.getIrcLibrary());
            XdccIrcClient client = new XdccIrcClient(ircClient, group, opts, opts.getVerbosity());
            List<PackResult> results = client.downloadAll();

            for (int i = 0; i < group.size(); i++) {
                XdccPack pack = group.get(i);
                PackResult result = i < results.size() ? results.get(i) : PackResult.failure(XdccError.DOWNLOAD_FAILED, "");
                printResult(pack, result, opts.getVerbosity());
            }
        }
    }

    private List<List<XdccPack>> groupByServer(List<XdccPack> packs) {
        List<List<XdccPack>> groups = new ArrayList<>();
        if (packs.isEmpty()) return groups;

        List<XdccPack> current = new ArrayList<>();
        current.add(packs.get(0));
        String lastAddr = packs.get(0).getServer().address();

        for (int i = 1; i < packs.size(); i++) {
            XdccPack pack = packs.get(i);
            if (pack.getServer().address().equals(lastAddr)) {
                current.add(pack);
            } else {
                groups.add(current);
                current = new ArrayList<>();
                current.add(pack);
                lastAddr = pack.getServer().address();
            }
        }
        groups.add(current);
        return groups;
    }

    private void printResult(XdccPack pack, PackResult result, int verbosity) {
        if (result instanceof PackResult.Success) {
            // Already printed by client during transfer
            return;
        }

        if (result instanceof PackResult.Failure failure) {
            XdccError error = failure.error();
            switch (error.getKind()) {
                case ALREADY_DOWNLOADED:
                    System.out.println("File already downloaded (skipping): " + pack.getFilename());
                    break;
                case BOT_DENIED:
                    String notice = failure.lastBotNotice();
                    if (!notice.isEmpty()) {
                        System.err.println("Bot denied XDCC request: " + notice);
                    } else {
                        System.err.println("Bot denied XDCC request for: " + pack.getFilename());
                    }
                    break;
                case BOT_NOT_FOUND:
                    System.err.println("Bot " + pack.getBot() + " not found on server " + pack.getServer().address());
                    break;
                case SERVER_UNREACHABLE:
                    System.err.println("Server unreachable (" + pack.getServer().address() + "): " + error.getMessage());
                    System.err.println("Tip: use --server to specify a different server");
                    break;
                case UNRECOVERABLE:
                    System.err.println("Unrecoverable error (IP banned?). Aborting.");
                    break;
                case TIMEOUT:
                    System.err.println("Download of pack #" + pack.getPackNumber() + " timed out after all retries");
                    break;
                case DOWNLOAD_FAILED:
                    System.err.println("Download of " + pack.getFilename() + " failed after all retries");
                    break;
                default:
                    System.err.println("Error downloading pack " + pack.getPackNumber() + ": " + error.getMessage());
            }
        }
    }
}
