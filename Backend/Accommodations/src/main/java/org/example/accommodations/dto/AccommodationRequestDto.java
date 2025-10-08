package org.example.accommodations.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class AccommodationRequestDto {
    private String name;
    private LocationDto location;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
    private Boolean autoConfirm;
    private String pricingMode;
    private List<String> photos;
    private List<UUID> amenities;
}
