package com.typeahead.trie;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Collections;
import java.util.function.Function;

@Component
public class Trie {
    public final Map<String, QueryStats> queryStats = new ConcurrentHashMap<>();
    private final TrieNode root = new TrieNode();

    public void insert(String query, QueryStats stats,
                       Function<String, Double> countScorer,
                       Function<String, Double> trendingScorer) {
        queryStats.put(query, stats);
        TrieNode current = root;
        String lowerQuery = query.toLowerCase();
        for (char c : lowerQuery.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new TrieNode());
            current.updateTop(query, countScorer, trendingScorer);
        }
    }

    public List<String> getTopForPrefix(String prefix, String mode) {
        TrieNode current = root;
        String lowerPrefix = prefix.toLowerCase();
        for (char c : lowerPrefix.toCharArray()) {
            current = current.children.get(c);
            if (current == null) return Collections.emptyList();
        }
        return "basic".equals(mode) ? current.topByCount : current.topByTrending;
    }
}
