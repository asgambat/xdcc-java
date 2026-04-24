package io.github.asgambat.xdcc.search;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.asgambat.xdcc.domain.IrcServer;
import io.github.asgambat.xdcc.domain.XdccPack;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Singleton
@Named("ixirc")
public class IxircEngine implements SearchEngine {

    private static final String BASE_URL = "https://ixirc.com/api/?q=";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "ixirc";
    }

    @Override
    public List<XdccPack> search(String term) throws IOException {
        List<XdccPack> results = new ArrayList<>();
        String encodedTerm = URLEncoder.encode(term, StandardCharsets.UTF_8);

        int page = 0;
        int totalPages = 1;

        while (page < totalPages) {
            String url = BASE_URL + encodedTerm + "&pn=" + page;
            JsonNode root;
            try (InputStream is = URI.create(url).toURL().openStream()) {
                root = objectMapper.readTree(is);
            }

            if (page == 0) {
                totalPages = root.path("pc").asInt(1);
            }

            JsonNode resultArray = root.path("results");
            if (!resultArray.isArray()) break;

            for (JsonNode item : resultArray) {
                String uname = item.path("uname").asText("").trim();
                if (uname.isEmpty()) continue;

                String serverAddr = item.path("naddr").asText("irc.rizon.net").trim();
                int port = item.path("nport").asInt(6667);
                int packNum = item.path("n").asInt(0);
                String filename = item.path("name").asText("").trim();
                long size = item.path("sz").asLong(0);

                if (packNum <= 0) continue;

                IrcServer server = new IrcServer(serverAddr, port);
                XdccPack pack = new XdccPack(server, uname, packNum);
                pack.setFilename(filename, true);
                pack.setSize(size);
                results.add(pack);
            }
            page++;
        }

        return results;
    }
}
