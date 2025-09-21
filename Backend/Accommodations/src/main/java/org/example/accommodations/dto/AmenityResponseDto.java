package org.example.accommodations.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AmenityResponseDto {
    private UUID id;
    private String name;
    private String description;
}
