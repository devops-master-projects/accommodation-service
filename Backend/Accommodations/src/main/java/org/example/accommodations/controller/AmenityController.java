package org.example.accommodations.controller;

import lombok.RequiredArgsConstructor;
import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.mappers.AmenityMapper;
import org.example.accommodations.service.AmenityService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/amenities")
@RequiredArgsConstructor
public class AmenityController {

    private final AmenityService service;

    @PostMapping
    public ResponseEntity<AmenityResponseDto> create(@RequestBody AmenityRequestDto request) {
        AmenityResponseDto response = service.create(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<AmenityResponseDto>> getAll() {
        return service.getAll();
    }

}
