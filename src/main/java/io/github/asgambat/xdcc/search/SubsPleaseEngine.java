package io.github.asgambat.xdcc.search;

import io.github.asgambat.xdcc.domain.IrcServer;
import io.github.asgambat.xdcc.domain.XdccPack;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@Named("subsplease")
public class SubsPleaseEngine implements SearchEngine {

    private static final String BASE_URL = "https://subsplease.org/xdcc/search.php?t=";
    private static final IrcServer SUBSPLEASE_SERVER = IrcServer.of("irc.rizon.net");
    // Matches: varname={b:"BotName",n:42,s:sizeInMB,f:"filename.mkv"}
    private static final Pattern ENTRY_PATTERN = Pattern.compile(
            "\\{b:\"([^\"]+)\",n:(\\d+),s:([0-9.]+),f:\"([^\"]+)\"\\}"
    );

    @Override
    public String name() {
        return "subsplease";
    }

    @Override
    public List<XdccPack> search(String term) throws IOException {
        String url = BASE_URL + URLEncoder.encode(term, StandardCharsets.UTF_8);
        List<XdccPack> results = new ArrayList<>();

        StringBuilder content = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(URI.create(url).toURL().openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
        }

        // Semicolon-separated entries
        String[] entries = content.toString().split(";");
        for (String entry : entries) {
            Matcher m = ENTRY_PATTERN.matcher(entry);
            if (!m.find()) continue;

            String bot = m.group(1);
            int packNum;
            try {
                packNum = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }
            double sizeMb;
            try {
                sizeMb = Double.parseDouble(m.group(3));
            } catch (NumberFormatException e) {
                sizeMb = 0;
            }
            String filename = m.group(4);

            XdccPack pack = new XdccPack(SUBSPLEASE_SERVER, bot, packNum);
            pack.setFilename(filename, true);
            pack.setSize((long) (sizeMb * 1_000_000));
            results.add(pack);
        }
        return results;
    }
}
