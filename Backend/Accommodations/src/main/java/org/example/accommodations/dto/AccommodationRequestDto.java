package org.example.accommodations.dto;

import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class AccommodationRequestDto {
    private UUID hostId;
    private String name;
    private LocationDto location;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
    private Boolean autoConfirm;
    private String pricingMode;
    private List<String> photos;
}
