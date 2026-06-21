package com.typeahead.trie;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class TrieNode {
    public final Map<Character, TrieNode> children = new ConcurrentHashMap<>();

    // Two independently-maintained top-10 lists so that the SAME node can serve
    // both ranking modes in O(1):
    //   - topByCount    : ranked purely by historical total_count (basic mode)
    //   - topByTrending : ranked by the blended recency-aware trending score
    // Keeping both precomputed is what makes basic mode a TRUE top-by-count list
    // rather than a re-sort of the trending list.
    public final List<String> topByCount = new CopyOnWriteArrayList<>();
    public final List<String> topByTrending = new CopyOnWriteArrayList<>();

    public void updateTop(String query, Function<String, Double> countScorer, Function<String, Double> trendingScorer) {
        updateList(topByCount, query, countScorer);
        updateList(topByTrending, query, trendingScorer);
    }

    private void updateList(List<String> list, String query, Function<String, Double> scorer) {
        if (!list.contains(query)) {
            list.add(query);
        }
        list.sort((a, b) -> Double.compare(scorer.apply(b), scorer.apply(a)));
        if (list.size() > 10) {
            list.remove(10);
        }
    }
}
