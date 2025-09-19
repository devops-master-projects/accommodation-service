package org.example.accommodations.service;


import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.repository.AccommodationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;

    public AccommodationService(AccommodationRepository accommodationRepository) {
        this.accommodationRepository = accommodationRepository;
    }

    public List<Accommodation> getAll() {
        return accommodationRepository.findAll();
    }

    public AccommodationResponseDto create(AccommodationRequestDto request) {
        Accommodation accommodation = Accommodation.builder()
                .hostId(request.getHostId())
                .name(request.getName())
                .location(request.getLocation())
                .minGuests(request.getMinGuests())
                .maxGuests(request.getMaxGuests())
                .description(request.getDescription())
                .autoConfirm(request.getAutoConfirm())
                .pricingMode(request.getPricingMode())
                .build();

        Accommodation saved = accommodationRepository.save(accommodation);

        AccommodationResponseDto response = new AccommodationResponseDto();
        response.setId(saved.getId());
        response.setName(saved.getName());
        response.setLocation(saved.getLocation());
        response.setMinGuests(saved.getMinGuests());
        response.setMaxGuests(saved.getMaxGuests());
        response.setDescription(saved.getDescription());

        return response;
    }

}