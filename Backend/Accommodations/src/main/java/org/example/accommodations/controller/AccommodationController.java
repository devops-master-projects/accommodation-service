package org.example.accommodations.controller;


import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.service.AccommodationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
}