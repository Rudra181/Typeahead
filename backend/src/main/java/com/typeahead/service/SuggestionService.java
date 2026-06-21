package com.typeahead.service;
import org.springframework.stereotype.Service;
import com.typeahead.trie.Trie;
import com.typeahead.trie.QueryStats;
import com.typeahead.model.SearchQuery;
import com.typeahead.repository.SearchQueryRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.Instant;

@Service
public class SuggestionService {
    public final Trie trie;
    private final SearchQueryRepository repo;
    private final TrendingScoreCalculator calc;
    private final BatchWriteService batch;

    public SuggestionService(Trie trie, SearchQueryRepository repo, TrendingScoreCalculator calc, BatchWriteService batch) {
        this.trie = trie;
        this.repo = repo;
        this.calc = calc;
        this.batch = batch;
    }

    // Scorer used to rank the "basic" list: pure historical popularity.
    private double countScore(String s) {
        QueryStats st = trie.queryStats.get(s);
        return st == null ? 0.0 : (double) st.totalCount;
    }

    // Scorer used to rank the "trending" list: blended recency-aware score.
    private double trendingScore(String s) {
        QueryStats st = trie.queryStats.get(s);
        return st == null ? 0.0 : calc.getTrendingScore(st);
    }

    @PostConstruct
    public void init() {
        System.out.println("Loading from DB...");
        List<SearchQuery> items = repo.findAll();
        for (SearchQuery q : items) {
            QueryStats stats = new QueryStats(q.getTotalCount(), q.getRecentCount24h(), q.getLastSearchedAt());
            trie.insert(q.getQueryText(), stats, this::countScore, this::trendingScore);
        }
    }

    public void processSearch(String q) {
        Instant now = Instant.now();
        QueryStats stats = trie.queryStats.computeIfAbsent(q, k -> new QueryStats(0, 0, now));
        stats.totalCount++;
        stats.decayScore = calc.computeDecay(stats, now) + 1;
        stats.lastSearchedAt = now;
        trie.insert(q, stats, this::countScore, this::trendingScore);
        batch.increment(q);
    }
    
    public List<Map<String, Object>> getDetails(List<String> queries, String mode) {
        List<Map<String, Object>> res = new ArrayList<>();
        for (String q : queries) {
            QueryStats stats = trie.queryStats.get(q);
            Map<String, Object> map = new HashMap<>();
            map.put("query", q);
            if (stats != null) {
                map.put("totalCount", stats.totalCount);
                map.put("trendingScore", calc.getTrendingScore(stats));
            }
            res.add(map);
        }
        if ("basic".equals(mode)) {
            res.sort((a, b) -> Long.compare((Long)b.get("totalCount"), (Long)a.get("totalCount")));
        } else {
            res.sort((a, b) -> Double.compare((Double)b.get("trendingScore"), (Double)a.get("trendingScore")));
        }
        return res;
    }
}
