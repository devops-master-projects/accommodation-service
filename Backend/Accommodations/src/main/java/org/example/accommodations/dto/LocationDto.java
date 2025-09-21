package org.example.accommodations.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LocationDto {
    private String country;
    private String city;
    private String address;
    private String postalCode;
}
