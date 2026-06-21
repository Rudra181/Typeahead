package com.typeahead.controller;
import org.springframework.web.bind.annotation.*;
import com.typeahead.service.SuggestionService;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {
    private final SuggestionService suggestionService;

    public SearchController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    static class SearchReq { public String query; }

    @PostMapping
    public Map<String, String> search(@RequestBody SearchReq req) {
        if (req != null && req.query != null && !req.query.isEmpty()) {
            suggestionService.processSearch(req.query);
        }
        return Map.of("message", "Searched");
    }
}
