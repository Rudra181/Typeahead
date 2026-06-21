package com.typeahead.controller;
import org.springframework.web.bind.annotation.*;
import com.typeahead.service.SuggestionService;
import com.typeahead.service.TrendingScoreCalculator;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/api/trending")
public class TrendingController {
    private final SuggestionService suggestionService;
    private final TrendingScoreCalculator calc;

    /** How long (ms) to serve the cached trending list before recomputing. */
    private static final long CACHE_TTL_MS = 30_000;

    /** Holds the last-computed top-10 list. Null until first request. */
    private final AtomicReference<List<Map<String, Object>>> cachedResult =
            new AtomicReference<>(null);

    /** Epoch-ms timestamp of the last full O(N) scan. Volatile for cross-thread visibility. */
    private volatile long lastComputedAt = 0;

    public TrendingController(SuggestionService suggestionService, TrendingScoreCalculator calc) {
        this.suggestionService = suggestionService;
        this.calc = calc;
    }

    @GetMapping
    public List<Map<String, Object>> trending() {
        long now = System.currentTimeMillis();

        // Return cached result if it is still fresh.
        List<Map<String, Object>> cached = cachedResult.get();
        if (cached != null && (now - lastComputedAt) < CACHE_TTL_MS) {
            return cached;
        }

        // Cache is stale (or this is the very first request) — recompute.
        List<Map<String, Object>> fresh = computeTopTrending();

        // Store atomically. If two threads race here, the last writer wins,
        // which is perfectly acceptable for a best-effort trending list.
        cachedResult.set(fresh);
        lastComputedAt = now;

        return fresh;
    }

    /** Full O(N) scan over queryStats — runs at most once per CACHE_TTL_MS. */
    private List<Map<String, Object>> computeTopTrending() {
        List<Map<String, Object>> all = new ArrayList<>();
        suggestionService.trie.queryStats.forEach((query, stats) -> {
            Map<String, Object> entry = new HashMap<>();
            entry.put("query", query);
            entry.put("trendingScore", calc.getTrendingScore(stats));
            all.add(entry);
        });
        all.sort((a, b) -> Double.compare(
                (Double) b.get("trendingScore"),
                (Double) a.get("trendingScore")));
        return List.copyOf(all.subList(0, Math.min(10, all.size())));
    }
}
