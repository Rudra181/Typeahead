package com.typeahead.service;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import java.sql.PreparedStatement;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.ZoneOffset;

@Service
public class BatchWriteService {
    // AtomicReference lets us swap the live buffer for a fresh one in a single
    // atomic step, so two threads that flush concurrently (e.g. one tripping the
    // size threshold while the scheduler also fires) can never both grab and
    // double-apply the same map of counts.
    private final AtomicReference<ConcurrentHashMap<String, LongAdder>> bufferRef =
            new AtomicReference<>(new ConcurrentHashMap<>());
    private final JdbcTemplate jdbcTemplate;

    @Value("${app.batch.flush-size}")
    private int flushSize;

    public BatchWriteService(JdbcTemplate jdbcTemplate) { this.jdbcTemplate = jdbcTemplate; }

    public void increment(String query) {
        ConcurrentHashMap<String, LongAdder> buffer = bufferRef.get();
        buffer.computeIfAbsent(query, k -> new LongAdder()).increment();
        if (buffer.size() >= flushSize) flush();
    }

    @Scheduled(fixedDelayString = "${app.batch.flush-interval-ms}")
    public void flush() {
        // Atomically detach the current buffer and install an empty one. Only the
        // thread that wins getAndSet owns `current`, so it is flushed exactly once.
        ConcurrentHashMap<String, LongAdder> current = bufferRef.getAndSet(new ConcurrentHashMap<>());
        if (current.isEmpty()) return;

        String sql = "INSERT INTO search_queries (query_text, total_count, recent_count_24h, last_searched_at) VALUES (?, ?, ?, ?) " +
                     "ON CONFLICT (query_text) DO UPDATE SET total_count = search_queries.total_count + EXCLUDED.total_count, " +
                     "recent_count_24h = search_queries.recent_count_24h + EXCLUDED.recent_count_24h, last_searched_at = EXCLUDED.last_searched_at";
                     
        jdbcTemplate.batchUpdate(sql, current.entrySet(), current.size(), (PreparedStatement ps, Map.Entry<String, LongAdder> entry) -> {
            ps.setString(1, entry.getKey());
            ps.setLong(2, entry.getValue().longValue());
            ps.setLong(3, entry.getValue().longValue()); // roughly mapping to recent
            ps.setObject(4, java.time.LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
        });
    }
}
