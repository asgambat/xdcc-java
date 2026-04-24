package io.github.asgambat.xdcc.command;

import io.github.asgambat.xdcc.domain.XdccPack;
import io.github.asgambat.xdcc.search.SearchEngine;
import jakarta.inject.Inject;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Command(name = "xdcc-search", description = "Search for XDCC packs", mixinStandardHelpOptions = true)
public class SearchCommand implements Runnable {

    @Parameters(index = "0", description = "Search term")
    private String term;

    @Parameters(index = "1", arity = "0..1", description = "Search engine name")
    private Optional<String> engineArg = Optional.empty();

    @Option(names = {"-e", "--search-engine"}, description = "Search engine (default: xdcc-eu)", defaultValue = "xdcc-eu")
    private String engineName;

    @Option(names = {"-v"}, description = "Verbose output", defaultValue = "false")
    private boolean verbose;

    @Inject
    private Collection<SearchEngine> engines;

    @Override
    public void run() {
        String effectiveEngine = engineArg.orElse(engineName);

        SearchEngine engine = engines.stream()
                .filter(e -> e.name().equals(effectiveEngine))
                .findFirst()
                .orElse(null);

        if (engine == null) {
            System.err.println("Unknown search engine: " + effectiveEngine);
            System.err.println("Available engines: " + engines.stream().map(SearchEngine::name).toList());
            System.exit(1);
            return;
        }

        List<XdccPack> results;
        try {
            results = engine.search(term);
        } catch (IOException e) {
            System.err.println("Search failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        if (results.isEmpty()) {
            System.out.println("No results found for: " + term);
            return;
        }

        for (XdccPack pack : results) {
            String msg = pack.getRequestMessage(true);
            String serverStr = pack.getServer().address().equals("irc.rizon.net") ? "" :
                    " --server " + pack.getServer().address();
            System.out.printf("%s [%s] (xdcc-dl \"%s\"%s)%n",
                    pack.getFilename(),
                    io.github.asgambat.xdcc.domain.XdccPack.humanReadableBytes(pack.getSize()),
                    msg,
                    serverStr);
        }
    }
}
