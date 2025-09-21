package org.example.accommodations.service;

import lombok.RequiredArgsConstructor;
import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.mappers.AmenityMapper;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AmenityService {

    private final AmenityRepository repository;

    public AmenityResponseDto create(AmenityRequestDto request) {
        if (repository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Amenity with this name already exists");
        }

        Amenity entity = AmenityMapper.toEntity(request);
        Amenity saved = repository.save(entity);
        return AmenityMapper.toDto(saved);
    }

    public ResponseEntity<List<AmenityResponseDto>> getAll() {
        List<AmenityResponseDto> list = repository.findAll()
                .stream()
                .map(AmenityMapper::toDto)
                .toList();

        return ResponseEntity.ok(list);
    }

}
