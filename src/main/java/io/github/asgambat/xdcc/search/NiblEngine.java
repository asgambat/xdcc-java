package io.github.asgambat.xdcc.search;

import io.github.asgambat.xdcc.domain.IrcServer;
import io.github.asgambat.xdcc.domain.XdccPack;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Named("nibl")
public class NiblEngine implements SearchEngine {

    private static final String BASE_URL = "https://nibl.co.uk/search?query=";
    private static final IrcServer NIBL_SERVER = IrcServer.of("irc.rizon.net");

    @Override
    public String name() {
        return "nibl";
    }

    @Override
    public List<XdccPack> search(String term) throws IOException {
        String url = BASE_URL + URLEncoder.encode(term, StandardCharsets.UTF_8);
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        List<XdccPack> results = new ArrayList<>();
        // Parse table rows: columns are [bot, packNum, size, filename]
        Elements rows = doc.select("table tbody tr");
        for (Element row : rows) {
            Elements cols = row.select("td");
            if (cols.size() < 4) continue;
            String bot = cols.get(0).text().trim();
            String packNumStr = cols.get(1).text().trim();
            String sizeStr = cols.get(2).text().trim();
            String filename = cols.get(3).text().trim();

            if (bot.isEmpty() || packNumStr.isEmpty()) continue;
            try {
                int packNum = Integer.parseInt(packNumStr);
                XdccPack pack = new XdccPack(NIBL_SERVER, bot, packNum);
                pack.setFilename(filename, true);
                try {
                    pack.setSize(io.github.asgambat.xdcc.parse.ThrottleParser.parseByteString(sizeStr));
                } catch (Exception ignored) {}
                results.add(pack);
            } catch (NumberFormatException ignored) {}
        }
        return results;
    }
}
