package org.example.accommodations.dto;

import lombok.Data;

@Data
public class AccommodationRequestDto {
    private Long hostId;
    private String name;
    private String location;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
    private Boolean autoConfirm;
    private String pricingMode;
}