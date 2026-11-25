package com.miranda.luna.store.infrastructure.web.controller;

import com.miranda.luna.store.application.service.StoreStatusService;
import com.miranda.luna.store.infrastructure.web.dto.StoreStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/store/status")
public class StoreStatusController {

    private final StoreStatusService storeStatusService;

    public StoreStatusController(StoreStatusService storeStatusService) {
        this.storeStatusService = storeStatusService;
    }

    @GetMapping
    public ResponseEntity<StoreStatusResponse> getStatus() {
        return ResponseEntity.ok(storeStatusService.getStatusSummary());
    }
}
