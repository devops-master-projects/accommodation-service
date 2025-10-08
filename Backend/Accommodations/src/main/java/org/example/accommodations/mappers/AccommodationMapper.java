package org.example.accommodations.mappers;

import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.dto.LocationDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.model.Location;
import org.example.accommodations.model.Photo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AccommodationMapper {

    public AccommodationResponseDto toDto(Accommodation accommodation) {
        return AccommodationResponseDto.builder()
                .id(accommodation.getId())
                .name(accommodation.getName())
                .minGuests(accommodation.getMinGuests())
                .maxGuests(accommodation.getMaxGuests())
                .description(accommodation.getDescription())
                .location(toLocationDto(accommodation.getLocation()))
                .hostId(accommodation.getHostId())
                .urlPhotos(accommodation.getPhotos() != null
                        ? accommodation.getPhotos().stream().map(Photo::getUrl).toList()
                        : null)
                .autoConfirm(accommodation.getAutoConfirm())
                .pricingMode(accommodation.getPricingMode())
                .amenities(accommodation.getAmenities() != null
                        ? accommodation.getAmenities().stream()
                        .map(a -> new AmenityResponseDto(
                                a.getId(),
                                a.getName(),
                                a.getDescription()
                        ))
                        .toList()
                        : List.of())
                .build();
    }

    private LocationDto toLocationDto(Location location) {
        if (location == null) return null;
        return LocationDto.builder()
                .country(location.getCountry())
                .city(location.getCity())
                .address(location.getAddress())
                .postalCode(location.getPostalCode())
                .build();
    }
}
