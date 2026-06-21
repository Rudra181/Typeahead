package com.typeahead.trie;
import java.time.Instant;

public class QueryStats {
    public long totalCount;
    public double decayScore;
    public Instant lastSearchedAt;
    
    public QueryStats(long totalCount, double decayScore, Instant lastSearchedAt) {
        this.totalCount = totalCount;
        this.decayScore = decayScore;
        this.lastSearchedAt = lastSearchedAt;
    }
}
