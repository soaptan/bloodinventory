package com.fyp.bloodinventory.controller;

import com.fyp.bloodinventory.dto.SmartSearchResultDto;
import com.fyp.bloodinventory.service.SmartSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/smart-search")
public class SmartSearchController {

    private final SmartSearchService smartSearchService;

    public SmartSearchController(SmartSearchService smartSearchService) {
        this.smartSearchService = smartSearchService;
    }

    @GetMapping
    public ResponseEntity<List<SmartSearchResultDto>> search(@RequestParam(value = "q", required = false) String query,
                                                             Authentication authentication) {
        return ResponseEntity.ok(smartSearchService.search(query, authorityNames(authentication)));
    }

    private Collection<String> authorityNames(Authentication authentication) {
        if (authentication == null) {
            return List.of();
        }

        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority() == null ? "" : authority.getAuthority())
                .filter(authority -> !authority.isBlank())
                .toList();
    }
}
