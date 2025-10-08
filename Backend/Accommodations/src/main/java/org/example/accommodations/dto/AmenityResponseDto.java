package org.example.accommodations.dto;

import lombok.*;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class AmenityResponseDto {
    private UUID id;
    private String name;
    private String description;
}
