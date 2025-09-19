package org.example.accommodations.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AccommodationResponseDto {
    private UUID id;
    private String name;
    private String location;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
}