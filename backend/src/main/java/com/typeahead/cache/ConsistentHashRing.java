package com.typeahead.cache;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.PostConstruct;
import java.security.MessageDigest;
import java.util.TreeMap;
import java.util.Map;

@Component
public class ConsistentHashRing {
    private final TreeMap<Long, String> ring = new TreeMap<>();
    private static final int VIRTUAL_NODES = 150;
    
    @Value("${app.redis.nodes}")
    private String redisNodes;

    @PostConstruct
    public void init() {
        String[] nodes = redisNodes.split(",");
        for (String node : nodes) {
            addNode(node.trim());
        }
    }

    private void addNode(String nodeId) {
        for (int i = 0; i < VIRTUAL_NODES; i++) {
            ring.put(sha256ToLong(nodeId + "#" + i), nodeId);
        }
    }

    public String getNode(String key) {
        if (ring.isEmpty()) return null;
        long h = sha256ToLong(key);
        Map.Entry<Long, String> entry = ring.ceilingEntry(h);
        return (entry != null) ? entry.getValue() : ring.firstEntry().getValue();
    }
    
    public long getHash(String key) { return sha256ToLong(key); }

    private long sha256ToLong(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(text.getBytes("UTF-8"));
            long val = 0;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | (bytes[i] & 0xff);
            }
            return val;
        } catch (Exception e) { throw new RuntimeException(e); }
    }
}
