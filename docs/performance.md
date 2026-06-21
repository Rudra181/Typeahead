# Performance Report: Typeahead Search Typeahead

This document details the performance characteristics of the Typeahead Search Typeahead system, measured using an automated benchmarking suite. The measurements evaluate latency, caching efficiency, write throughput, and the consistent hashing distribution.

## 1. Latency & Caching Performance

The suggestion API (`GET /api/suggest`) is highly optimized for low latency, utilizing an in-memory Trie for cache misses and a distributed Redis layer for cache hits.

**Methodology:** 2,000 requests issued by 20 concurrent workers.

### 1.1 Cold Cache Latency (Trie Reads)
When the cache is entirely empty (simulated by querying unique random prefixes), every request falls back to the in-memory Trie.

* **p50:** 9.96 ms
* **p95:** 20.20 ms
* **p99:** 34.80 ms
* **Mean:** 11.12 ms

*Analysis:* Even on a cache miss, reading directly from the pre-computed in-memory Trie provides excellent sub-25ms p95 latency. The database is entirely bypassed during read operations.

### 1.2 Warm Cache Latency (Redis Reads)
When the cache is warmed up with common prefixes, requests are served directly from the Redis consistent hash ring.

* **p50:** 9.64 ms
* **p95:** 16.92 ms
* **p99:** 26.03 ms
* **Mean:** 10.28 ms
* **Cache Hit Rate:** 100%

*Analysis:* Redis provides a slight reduction in p95 and p99 tail latencies. More importantly, serving from Redis drastically reduces the CPU load on the Spring Boot application, as it completely avoids Trie traversal and object serialization for hot queries.

## 2. Search Submission Throughput & Write Reduction

The search submission API (`POST /api/search`) uses an asynchronous batch-writing mechanism (`BatchWriteService`) to avoid synchronous database locks.

**Methodology:** 2,000 search submissions fired rapidly by 20 concurrent workers.

* **Throughput:** 1,856.2 requests/sec
* **Total Time:** 1.08 seconds
* **Latency (p95):** 15.91 ms
* **Success Rate:** 100% (2000/2000)

### 2.1 Batch Write Reduction Ratio
Instead of executing 2,000 individual `INSERT/UPDATE` statements synchronously, the system buffers updates in a `ConcurrentHashMap` with `LongAdder` counters. 
Because the flush interval is set to 5 seconds (or 500 distinct items), the 2,000 requests processed in 1.08 seconds resulted in exactly **1 database write operation** (a single JDBC `executeBatch()` with an UPSERT statement).

This represents a **2000x reduction** in database write traffic, completely shielding PostgreSQL from high-concurrency traffic spikes.

## 3. Consistent Hashing Distribution

The system avoids Redis Cluster in favor of a custom Consistent Hashing ring implemented in the application layer. This ensures that the loss or addition of a cache node only remaps $K/N$ keys.

**Methodology:** 66 unique prefixes routed through the `ConsistentHashRing`.

* **redis-node-1:** 19 keys (28.8%)
* **redis-node-2:** 27 keys (40.9%)
* **redis-node-3:** 20 keys (30.3%)

*Analysis:* Using 150 virtual nodes per physical instance achieves a relatively even distribution across the three physical nodes without relying on a central coordinator.

## 4. Trending vs. Basic Ranking Evaluation

The system serves two ranking modes from the **same** `GET /api/suggest` endpoint, each
backed by an independently-maintained top-10 list at every Trie node:

* `mode=basic` &rarr; ordered purely by historical `total_count`.
* `mode=trending` &rarr; ordered by the blended recency-aware score.

To exercise the difference we let a query trend **live**, the way real breaking news behaves. For
prefix `b`, the baseline `basic` and `trending` top-5 are identical (nothing under `b` has recent
activity yet). We then make a **brand-new** query `"bzz breaking news demo"` — it shares the prefix
but carries ~0 historical count — receive **500 searches** in a short window, and re-read both modes.

**Result (measured):**

| Mode | Top-5 after 500 recent searches |
|---|---|
| **Basic** (unchanged) | `B"H`, `B&B Hotels p/b KTM`, `B&D Australia Pty Ltd.`, `B&G`, `B&H Dairy` |
| **Trending** | **`bzz breaking news demo`** (#1), `B"H`, `B&B Hotels p/b KTM`, `B&D Australia Pty Ltd.`, `B&G` |

* In **basic** mode the new query never appears in the top-10 — its `total_count` (500) is dwarfed
  by the historical leaders (200 000).
* In **trending** mode it jumps straight to **#1**, with `trendingScore ≈ 1502.67`.

The score is exactly what the formula predicts:

```
trendingScore("bzz breaking news demo") = 1.0·log₁₀(1+500) + 3.0·500
                                        = 2.70 + 1500.00 = 1502.70   (measured 1502.67)
trendingScore("B\"H")                    = 1.0·log₁₀(1+200000) + 3.0·0
                                        = 5.30                        (measured 5.30)
```

The same prefix, served by the same API, returns two divergent answers — proving the recency-aware
ranking is real and not a re-sort of the historical list. As the 500 recent hits age past the 6-hour
half-life, the decay term shrinks and the query falls back toward its historical baseline, which is
how the system avoids permanently over-ranking a short-lived spike.
