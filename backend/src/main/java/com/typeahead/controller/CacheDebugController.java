package com.typeahead.controller;
import org.springframework.web.bind.annotation.*;
import com.typeahead.cache.ConsistentHashRing;
import com.typeahead.cache.RedisNodeClient;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/cache")
public class CacheDebugController {
    private final ConsistentHashRing ring;
    private final RedisNodeClient redis;

    public CacheDebugController(ConsistentHashRing ring, RedisNodeClient redis) {
        this.ring = ring;
        this.redis = redis;
    }

    @GetMapping("/debug")
    public Map<String, Object> debug(@RequestParam("prefix") String prefix) {
        String cacheKey = "suggest:trending:" + prefix.toLowerCase();
        String node = ring.getNode(cacheKey);
        long hash = ring.getHash(cacheKey);
        
        String val = node != null ? redis.get(node, cacheKey) : null;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("prefix", prefix);
        res.put("cacheKey", cacheKey);
        res.put("hash", hash);
        res.put("assignedNode", node);
        if (val != null) {
            res.put("status", "HIT");
            res.put("ttlRemainingSeconds", redis.ttl(node, cacheKey));
        } else {
            res.put("status", "MISS");
        }
        return res;
    }
}
