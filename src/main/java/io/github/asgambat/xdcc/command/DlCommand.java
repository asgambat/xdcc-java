package io.github.asgambat.xdcc.command;

import io.github.asgambat.xdcc.domain.XdccPack;
import io.github.asgambat.xdcc.downloader.XdccDownloader;
import io.github.asgambat.xdcc.irc.DownloadOptions;
import io.github.asgambat.xdcc.parse.XdccMessageParser;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

@Command(name = "xdcc-dl", description = "Download files via XDCC", mixinStandardHelpOptions = true)
public class DlCommand implements Runnable {

    @Parameters(index = "0", description = "XDCC message (e.g. /msg BotName xdcc send #42)")
    private String message;

    @CommandLine.Option(names = {"--no-pack-limit"}, description = "Disable the pack range limit (default max: 20)", defaultValue = "false")
    private boolean noPackLimit;

    @CommandLine.Mixin
    private DownloadOptionsMixin dlOpts;

    @Inject
    private XdccDownloader downloader;

    @Override
    public void run() {
        List<XdccPack> packs;
        try {
            int maxRange = noPackLimit ? 0 : 20;
            packs = XdccMessageParser.parse(message,
                    dlOpts.out.isEmpty() ? null : dlOpts.out, dlOpts.server, maxRange);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (!dlOpts.out.isEmpty()) {
            XdccMessageParser.preparePacks(packs, dlOpts.out);
        }

        DownloadOptions opts = dlOpts.buildOptions();
        if (opts == null) {
            System.exit(1);
            return;
        }

        downloader.downloadPacks(packs, opts);
    }
}
