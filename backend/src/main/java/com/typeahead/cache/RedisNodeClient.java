package com.typeahead.cache;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RedisNodeClient {
    private final Map<String, JedisPool> pools = new ConcurrentHashMap<>();
    
    @Value("${app.redis.nodes}")
    private String redisNodes;

    @PostConstruct
    public void init() {
        String[] nodes = redisNodes.split(",");
        for (String node : nodes) {
            String[] parts = node.split(":");
            pools.put(node.trim(), new JedisPool(parts[0], Integer.parseInt(parts[1])));
        }
    }

    public String get(String node, String key) {
        if (pools.containsKey(node)) {
            try (Jedis jedis = pools.get(node).getResource()) {
                return jedis.get(key);
            } catch(Exception e) { return null; }
        }
        return null;
    }
    
    public void setex(String node, String key, int ttl, String value) {
        if (pools.containsKey(node)) {
            try (Jedis jedis = pools.get(node).getResource()) {
                jedis.setex(key, ttl, value);
            } catch(Exception e) { }
        }
    }
    
    public Long ttl(String node, String key) {
        if (pools.containsKey(node)) {
            try (Jedis jedis = pools.get(node).getResource()) {
                return jedis.ttl(key);
            } catch(Exception e) { return null; }
        }
        return null;
    }
}
