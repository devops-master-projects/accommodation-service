package org.example.accommodations.service;

import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.model.Location;
import org.example.accommodations.model.Photo;
import org.example.accommodations.repository.AccommodationRepository;
import org.example.accommodations.mappers.AccommodationMapper;
import org.example.accommodations.repository.AmenityRepository;
import org.springframework.stereotype.Service;

import java.util.*;

import java.util.stream.Collectors;

@Service
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationMapper accommodationMapper;
    private final AmenityRepository amenityRepository;

    public AccommodationService(AccommodationRepository accommodationRepository, AccommodationMapper accommodationMapper,
                                AmenityRepository amenityRepository) {
        this.accommodationRepository = accommodationRepository;
        this.accommodationMapper = accommodationMapper;
        this.amenityRepository = amenityRepository;
    }

    @Transactional
    public AccommodationResponseDto getById(UUID id) {
        Accommodation accommodation = accommodationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Accommodation not found: " + id));
        return accommodationMapper.toDto(accommodation);
    }

    public List<AccommodationResponseDto> getAll() {
        return accommodationRepository.findAllWithDetails()
                .stream()
                .map(accommodationMapper::toDto)
                .toList();
    }

    @Transactional
    public AccommodationResponseDto update(UUID id, AccommodationRequestDto request) {
        Accommodation accommodation = accommodationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Accommodation not found: " + id));

        // update basic fields
        accommodation.setName(request.getName());
        accommodation.setMinGuests(request.getMinGuests());
        accommodation.setMaxGuests(request.getMaxGuests());
        accommodation.setDescription(request.getDescription());
        accommodation.setAutoConfirm(request.getAutoConfirm());
        accommodation.setPricingMode(request.getPricingMode());

        // update location
        Location location = accommodation.getLocation();
        if (location == null) {
            location = new Location();
        }
        location.setCountry(request.getLocation().getCountry());
        location.setCity(request.getLocation().getCity());
        location.setAddress(request.getLocation().getAddress());
        location.setPostalCode(request.getLocation().getPostalCode());
        accommodation.setLocation(location);

        List<Photo> newPhotos = request.getPhotos() != null
                ? request.getPhotos().stream()
                .map(url -> Photo.builder()
                        .url(url)
                        .accommodation(accommodation)
                        .build())
                .toList()
                : List.of();

        accommodation.getPhotos().clear();
        accommodation.getPhotos().addAll(newPhotos);

        Set<Amenity> amenities = request.getAmenities() != null
                ? request.getAmenities().stream()
                .map(amenityRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet())
                : new HashSet<>();

        accommodation.setAmenities(amenities);


        Accommodation saved = accommodationRepository.save(accommodation);
        return accommodationMapper.toDto(saved);
    }


    @Transactional
    public AccommodationResponseDto create(AccommodationRequestDto request) {
        Location location = Location.builder()
                .country(request.getLocation().getCountry())
                .city(request.getLocation().getCity())
                .address(request.getLocation().getAddress())
                .postalCode(request.getLocation().getPostalCode())
                .build();

        Set<Photo> photos = request.getPhotos() != null
                ? request.getPhotos().stream()
                .map(url -> Photo.builder().url(url).build())
                .collect(Collectors.toSet())
                : new HashSet<>();

        Set<Amenity> amenities = request.getAmenities() != null
                ? request.getAmenities().stream()
                .map(id -> amenityRepository.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Amenity not found: " + id)))
                .collect(Collectors.toSet())
                : new HashSet<>();

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
                .amenities(amenities)
                .build();

        // Postavi bi-directional vezu za photos
        photos.forEach(p -> p.setAccommodation(accommodation));

        Accommodation saved = accommodationRepository.save(accommodation);
        return accommodationMapper.toDto(saved);
    }

}
