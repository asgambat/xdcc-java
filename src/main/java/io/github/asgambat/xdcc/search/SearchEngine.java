package io.github.asgambat.xdcc.search;

import io.github.asgambat.xdcc.domain.XdccPack;

import java.io.IOException;
import java.util.List;

public interface SearchEngine {
    String name();
    List<XdccPack> search(String term) throws IOException;
}
