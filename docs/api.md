# Search Typeahead API Documentation

This document describes the API endpoints exposed by the Typeahead Search Typeahead Backend. All API paths are prefixed with `/api`.

---

## 1. Get Typeahead Suggestions
Retrieves typeahead suggestions for a given query prefix. It uses the consistent-hashed Redis cache layer as the primary path and falls back to the in-memory Trie on cache misses.

* **Endpoint:** `GET /api/suggest`
* **Query Parameters:**
  * `q` (string, optional, default: `""`): The prefix string being typed by the user.
  * `mode` (string, optional, default: `"trending"`): The ranking mode. Supported values:
    * `basic`: Sorts suggestions purely by historical query counts (`total_count` descending).
    * `trending`: Sorts suggestions using a recency-aware blended formula combining historical counts and exponential time-decay scores.
* **Success Response (Cache Hit):**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    {
      "prefix": "iph",
      "mode": "trending",
      "source": "cache",
      "tookMs": 3,
      "suggestions": [
        {
          "query": "iphone 17 launch",
          "totalCount": 312,
          "trendingScore": 152.5
        },
        {
          "query": "iphone 15",
          "totalCount": 85000,
          "trendingScore": 4.93
        }
      ]
    }
    ```
* **Success Response (Cache Miss):**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    {
      "prefix": "iph",
      "mode": "trending",
      "source": "trie",
      "tookMs": 24,
      "suggestions": [
        {
          "query": "iphone 17 launch",
          "totalCount": 312,
          "trendingScore": 152.5
        },
        {
          "query": "iphone 15",
          "totalCount": 85000,
          "trendingScore": 4.93
        }
      ]
    }
    ```
* **Edge Cases:**
  * Empty query (`q=`) returns `200 OK` with an empty array of suggestions:
    ```json
    {
      "prefix": "",
      "mode": "trending",
      "suggestions": [],
      "tookMs": 0
    }
    ```
  * Query with mixed casing is automatically normalized to lowercase internally to match indices, returning suggestions with correct casing mapping.
  * Queries with no matching prefixes return an empty list of suggestions:
    ```json
    {
      "prefix": "nonexistentprefix",
      "mode": "trending",
      "source": "trie",
      "suggestions": [],
      "tookMs": 1
    }
    ```

---

## 2. Submit Search Query
Records a new search event. The request is processed asynchronously in the backend: it updates the in-memory Trie and appends the search count update to a local batch buffer (`ConcurrentHashMap` with `LongAdder`). The buffer is flushed to PostgreSQL periodically or on reaching a threshold batch size, keeping write traffic light.

* **Endpoint:** `POST /api/search`
* **Headers:** `Content-Type: application/json`
* **Request Body:**
  ```json
  {
    "query": "iphone 17 launch"
  }
  ```
* **Success Response:**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    {
      "message": "Searched"
    }
    ```

---

## 3. Cache Debug Route
A diagnostic endpoint that shows consistent-hashing mapping details for a query prefix, demonstrating which Redis node is responsible for storing its cache entries and its current TTL / cache status.

* **Endpoint:** `GET /api/cache/debug`
* **Query Parameters:**
  * `prefix` (string, required): The query prefix to trace.
* **Success Response (Cache Hit):**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    {
      "prefix": "iphone",
      "cacheKey": "suggest:trending:iphone",
      "hash": 8839201773344552192,
      "assignedNode": "redis-node-2:6379",
      "status": "HIT",
      "ttlRemainingSeconds": 42
    }
    ```
* **Success Response (Cache Miss):**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    {
      "prefix": "iphone",
      "cacheKey": "suggest:trending:iphone",
      "hash": 8839201773344552192,
      "assignedNode": "redis-node-2:6379",
      "status": "MISS"
    }
    ```

---

## 4. Get Global Trending Queries
Retrieves the overall top-10 queries by blended `trendingScore` across all queries currently indexed in the system. Used to populate the "Trending searches" dashboard on the home page.

* **Endpoint:** `GET /api/trending`
* **Success Response:**
  * **Code:** `200 OK`
  * **Content:**
    ```json
    [
      {
        "query": "iphone 17 launch",
        "trendingScore": 152.5
      },
      {
        "query": "A!MS",
        "trendingScore": 5.301
      },
      {
        "query": "A$AP Bari",
        "trendingScore": 5.301
      }
    ]
    ```
