package org.example.accommodations.mappers;

import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.model.Amenity;

public class AmenityMapper {

    public static Amenity toEntity(AmenityRequestDto dto) {
        return Amenity.builder()
                .name(dto.getName())
                .description(dto.getDescription())
                .build();
    }

    public static AmenityResponseDto toDto(Amenity entity) {
        AmenityResponseDto dto = new AmenityResponseDto();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        return dto;
    }
}