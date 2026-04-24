package io.github.asgambat.xdcc;

import io.github.asgambat.xdcc.command.BrowseCommand;
import io.github.asgambat.xdcc.command.DlCommand;
import io.github.asgambat.xdcc.command.SearchCommand;
import io.micronaut.configuration.picocli.PicocliRunner;
import picocli.CommandLine.Command;

@Command(
        name = "xdcc",
        description = "XDCC IRC downloader",
        subcommands = {DlCommand.class, SearchCommand.class, BrowseCommand.class},
        mixinStandardHelpOptions = true
)
public class XdccApplication implements Runnable {

    public static void main(String[] args) {
        // Prevent Netty from using sun.misc.Unsafe (suppresses JVM deprecation warnings)
        System.setProperty("io.netty.noUnsafe", "true");
        System.exit(PicocliRunner.execute(XdccApplication.class, args));
    }

    @Override
    public void run() {
        System.out.println("Use one of: xdcc-dl, xdcc-search, xdcc-browse");
        System.out.println("Run with --help for more information.");
    }
}
