package org.example.accommodations.dto;

import lombok.Data;

@Data
public class AccommodationResponseDto {
    private Long id;
    private String name;
    private String location;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
}