package org.example.accommodations.service;

import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.model.Location;
import org.example.accommodations.model.Photo;
import org.example.accommodations.repository.AccommodationRepository;
import org.example.accommodations.mappers.AccommodationMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationMapper accommodationMapper;

    public AccommodationService(AccommodationRepository accommodationRepository, AccommodationMapper accommodationMapper) {
        this.accommodationRepository = accommodationRepository;
        this.accommodationMapper = accommodationMapper;
    }

    public List<AccommodationResponseDto> getAll() {
        return accommodationRepository.findAll()
                .stream()
                .map(accommodationMapper::toDto)
                .toList();
    }

    public AccommodationResponseDto create(AccommodationRequestDto request) {
        Location location = Location.builder()
                .country(request.getLocation().getCountry())
                .city(request.getLocation().getCity())
                .address(request.getLocation().getAddress())
                .postalCode(request.getLocation().getPostalCode())
                .build();

        List<Photo> photos = request.getPhotos() != null
                ? request.getPhotos().stream()
                .map(url -> Photo.builder().url(url).build())
                .toList()
                : List.of();

        Accommodation accommodation = Accommodation.builder()
                .hostId(request.getHostId())
                .name(request.getName())
                .location(location)
                .minGuests(request.getMinGuests())
                .maxGuests(request.getMaxGuests())
                .description(request.getDescription())
                .autoConfirm(request.getAutoConfirm())
                .pricingMode(request.getPricingMode())
                .photos(photos)
                .build();

        photos.forEach(p -> p.setAccommodation(accommodation));
        Accommodation saved = accommodationRepository.save(accommodation);
        return accommodationMapper.toDto(saved);
    }
}
