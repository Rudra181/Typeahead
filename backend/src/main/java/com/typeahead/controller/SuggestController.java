package com.typeahead.controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;
import com.typeahead.service.SuggestionService;
import com.typeahead.cache.ConsistentHashRing;
import com.typeahead.cache.RedisNodeClient;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api")
public class SuggestController {

    private final SuggestionService suggestionService;
    private final ConsistentHashRing ring;
    private final RedisNodeClient redis;
    private final ObjectMapper mapper = new ObjectMapper();
    
    @Value("${app.redis.ttl-seconds}")
    private int ttl;

    public SuggestController(SuggestionService suggestionService, ConsistentHashRing ring, RedisNodeClient redis) {
        this.suggestionService = suggestionService;
        this.ring = ring;
        this.redis = redis;
    }

    @GetMapping("/suggest")
    public Map<String, Object> suggest(@RequestParam(value = "q", defaultValue="") String q,
                                       @RequestParam(value = "mode", defaultValue="trending") String mode) throws Exception {
        long start = System.currentTimeMillis();
        Map<String, Object> res = new HashMap<>();
        res.put("prefix", q);
        res.put("mode", mode);
        
        if (q.isEmpty()) {
            res.put("suggestions", List.of());
            res.put("tookMs", 0);
            return res;
        }

        String cacheKey = "suggest:" + mode + ":" + q.toLowerCase();
        String node = ring.getNode(cacheKey);
        
        String cached = node != null ? redis.get(node, cacheKey) : null;
        if (cached != null) {
            res.put("source", "cache");
            res.put("suggestions", mapper.readValue(cached, List.class));
            res.put("tookMs", System.currentTimeMillis() - start);
            return res;
        }
        
        List<String> topStrs = suggestionService.trie.getTopForPrefix(q, mode);
        List<Map<String, Object>> details = suggestionService.getDetails(topStrs, mode);
        
        res.put("source", "trie");
        res.put("suggestions", details);
        res.put("tookMs", System.currentTimeMillis() - start);
        
        if (node != null) {
            redis.setex(node, cacheKey, ttl, mapper.writeValueAsString(details));
        }
        return res;
    }
}
