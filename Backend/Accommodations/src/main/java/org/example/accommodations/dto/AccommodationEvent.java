package org.example.accommodations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AccommodationEvent {
    private String eventType;
    private String id;
    private String hostId;
    private String name;
    private String description;
    private Integer minGuests;
    private Integer maxGuests;
    private boolean autoConfirm;
    private String pricingMode;
    private LocationDto location;
    private List<String> amenities;
    private List<String> photos;

    // get/set/constructor
}
