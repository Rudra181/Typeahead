package com.typeahead.service;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import com.typeahead.trie.QueryStats;
import java.time.Instant;
import java.time.Duration;

@Service
public class TrendingScoreCalculator {
    @Value("${app.trending.half-life-hours}")
    private double halfLifeHours;
    
    @Value("${app.trending.weight-hist}")
    private double weightHist;
    
    @Value("${app.trending.weight-recent}")
    private double weightRecent;

    public double computeDecay(QueryStats stats, Instant now) {
        if (stats.lastSearchedAt == null) return stats.decayScore;
        double lambda = Math.log(2) / halfLifeHours;
        double dtHours = Duration.between(stats.lastSearchedAt, now).toMillis() / 3600000.0;
        return stats.decayScore * Math.exp(-lambda * dtHours);
    }
    
    public double getTrendingScore(QueryStats stats) {
        return weightHist * Math.log10(1 + stats.totalCount) + weightRecent * stats.decayScore;
    }
    
    public double getBasicScore(QueryStats stats) {
        return stats.totalCount;
    }
}
