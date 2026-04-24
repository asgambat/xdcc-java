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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Singleton
@Named("xdcc-eu")
public class XdccEuEngine implements SearchEngine {

    private static final String BASE_URL = "https://www.xdcc.eu/search.php?searchkey=";
    private static final Pattern PACK_PATTERN = Pattern.compile("([^ ]+) xdcc send #(\\d+)");

    private boolean verbose = false;

    @Override
    public String name() {
        return "xdcc-eu";
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<XdccPack> search(String term) throws IOException {
        String url = BASE_URL + URLEncoder.encode(term, StandardCharsets.UTF_8);
        if (verbose) {
            System.out.println("[xdcc-eu] Fetching: " + url);
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(15000)
                .get();

        List<XdccPack> results = new ArrayList<>();
        Elements rows = doc.select("tbody tr");

        for (Element row : rows) {
            Elements cols = row.select("td");
            if (cols.size() < 7) continue;

            Element linkCell = cols.get(1);
            Element anchor = linkCell.selectFirst("a[data-s][data-p]");
            if (anchor == null) continue;

            String serverAddr = anchor.attr("data-s").trim();
            String dataP = anchor.attr("data-p").trim();

            Matcher m = PACK_PATTERN.matcher(dataP);
            if (!m.find()) continue;

            String bot = m.group(1);
            int packNum;
            try {
                packNum = Integer.parseInt(m.group(2));
            } catch (NumberFormatException e) {
                continue;
            }

            String sizeStr = cols.get(5).text().trim();
            // Remove non-numeric prefix like "≈"
            sizeStr = sizeStr.replaceAll("^[^0-9]*", "");
            String filename = cols.get(6).text().trim();

            IrcServer server = serverAddr.isEmpty() ? IrcServer.of("irc.rizon.net") : IrcServer.parse(serverAddr);
            XdccPack pack = new XdccPack(server, bot, packNum);
            pack.setFilename(filename, true);
            try {
                pack.setSize(io.github.asgambat.xdcc.parse.ThrottleParser.parseByteString(sizeStr));
            } catch (Exception ignored) {}

            if (verbose) {
                System.out.printf("[xdcc-eu] Found: %s #%d %s on %s%n", bot, packNum, filename, server);
            }
            results.add(pack);
        }
        return results;
    }
}
