package org.example.accommodations.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccommodationResponseDto {
    private UUID id;
    private UUID hostId;
    private String name;
    private Integer minGuests;
    private Integer maxGuests;
    private String description;
    private List<String> urlPhotos;
    private LocationDto location;
    private Boolean autoConfirm;
    private String pricingMode;
    private List<AmenityResponseDto> amenities;
}