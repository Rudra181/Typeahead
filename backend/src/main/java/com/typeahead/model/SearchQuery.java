package com.typeahead.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "search_queries")
public class SearchQuery {
    @Id
    private String queryText;
    private long totalCount;
    private long recentCount_24h;
    private Instant lastSearchedAt;
    private Instant createdAt = Instant.now();

    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public long getTotalCount() { return totalCount; }
    public void setTotalCount(long totalCount) { this.totalCount = totalCount; }
    public long getRecentCount24h() { return recentCount_24h; }
    public void setRecentCount24h(long recentCount_24h) { this.recentCount_24h = recentCount_24h; }
    public Instant getLastSearchedAt() { return lastSearchedAt; }
    public void setLastSearchedAt(Instant lastSearchedAt) { this.lastSearchedAt = lastSearchedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
