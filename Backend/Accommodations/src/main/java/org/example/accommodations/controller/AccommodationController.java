package org.example.accommodations.controller;


import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.service.AccommodationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accommodations")
public class AccommodationController {

    private final AccommodationService accommodationService;

    public AccommodationController(AccommodationService accommodationService) {
        this.accommodationService = accommodationService;
    }

    @GetMapping
    public List<AccommodationResponseDto> getAll() {
        return accommodationService.getAll();
    }


    @PostMapping
    public AccommodationResponseDto create(@RequestBody AccommodationRequestDto request) {
        return accommodationService.create(request);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AccommodationResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(accommodationService.getById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AccommodationResponseDto> update(
            @PathVariable UUID id,
            @RequestBody AccommodationRequestDto request
    ) {
        return ResponseEntity.ok(accommodationService.update(id, request));
    }
}