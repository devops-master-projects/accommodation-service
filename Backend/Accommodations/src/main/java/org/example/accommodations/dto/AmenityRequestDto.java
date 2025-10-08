package org.example.accommodations.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
public class AmenityRequestDto {
    private String name;
    private String description;
}
