package org.example.accommodations.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.example.accommodations.dto.AccommodationEvent;
import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.LocationDto;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.model.Location;
import org.example.accommodations.model.Photo;
import org.example.accommodations.repository.AccommodationRepository;
import org.example.accommodations.mappers.AccommodationMapper;
import org.example.accommodations.repository.AmenityRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

import java.util.stream.Collectors;

@Service
public class AccommodationService {

    private final AccommodationRepository accommodationRepository;
    private final AccommodationMapper accommodationMapper;
    private final AmenityRepository amenityRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;


    public AccommodationService(AccommodationRepository accommodationRepository, AccommodationMapper accommodationMapper,
                                AmenityRepository amenityRepository, KafkaTemplate<String, String> kafkaTemplate,
                                ObjectMapper objectMapper) {
        this.accommodationRepository = accommodationRepository;
        this.accommodationMapper = accommodationMapper;
        this.amenityRepository = amenityRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccommodationResponseDto getById(UUID id) {
        Accommodation accommodation = accommodationRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new EntityNotFoundException("Accommodation not found: " + id));
        return accommodationMapper.toDto(accommodation);
    }

    public UUID getHostId(UUID accommodationId) {
        Accommodation accommodation = accommodationRepository.findById(accommodationId)
                .orElseThrow(() -> new IllegalArgumentException("Accommodation not found with id=" + accommodationId));
        return accommodation.getHostId();
    }


    public List<AccommodationResponseDto> getAll()  {
        return accommodationRepository.findAllWithDetails()
                .stream()
                .map(accommodationMapper::toDto)
                .toList();
    }

    @Transactional
    public AccommodationResponseDto updateAutoConfirm(UUID id, boolean autoConfirm) {
        Accommodation accommodation = accommodationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Accommodation not found: " + id));

        accommodation.setAutoConfirm(autoConfirm);
        Accommodation saved = accommodationRepository.save(accommodation);
        sendAccommodationUpdatedEvent(saved);

        return accommodationMapper.toDto(saved);
    }

    @Transactional
    public boolean getAutoConfirm(UUID id) {
        Accommodation accommodation = accommodationRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Accommodation not found: " + id));
        return accommodation.getAutoConfirm();
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
        sendAccommodationUpdatedEvent(saved);
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

        photos.forEach(p -> p.setAccommodation(accommodation));

        Accommodation saved = accommodationRepository.save(accommodation);
        sendAccommodationCreatedEvent(saved);

        return accommodationMapper.toDto(saved);
    }

    private void sendAccommodationCreatedEvent(Accommodation saved) {
        AccommodationEvent event = new AccommodationEvent(
                "AccommodationCreated",
                saved.getId().toString(),
                saved.getHostId().toString(),
                saved.getName(),
                saved.getDescription(),
                saved.getMinGuests(),
                saved.getMaxGuests(),
                saved.getAutoConfirm(),
                saved.getPricingMode(),
                new LocationDto(
                        saved.getLocation().getCountry(),
                        saved.getLocation().getCity(),
                        saved.getLocation().getAddress(),
                        saved.getLocation().getPostalCode()
                ),
                saved.getAmenities().stream()
                        .map(Amenity::getName)
                        .collect(Collectors.toList()),
                saved.getPhotos().stream()
                        .map(Photo::getUrl)
                        .collect(Collectors.toList())
        );

        try {
            String json = objectMapper.writeValueAsString(event);
            kafkaTemplate.send("accommodation-events", saved.getId().toString(), json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AccommodationEvent", e);
        }
    }

    private void sendAccommodationUpdatedEvent(Accommodation updated) {
        LocationDto locationDto = new LocationDto();
        locationDto.setAddress(updated.getLocation().getAddress());
        locationDto.setCity(updated.getLocation().getCity());
        locationDto.setCountry(updated.getLocation().getCountry());
        locationDto.setPostalCode(updated.getLocation().getPostalCode());

        System.out.println("country: " + locationDto.getCountry());
        AccommodationEvent event = new AccommodationEvent(
                "AccommodationUpdated",
                updated.getId().toString(),
                updated.getHostId().toString(),
                updated.getName(),
                updated.getDescription(),
                updated.getMinGuests(),
                updated.getMaxGuests(),
                updated.getAutoConfirm(),
                updated.getPricingMode(),
                locationDto,
                updated.getAmenities().stream()
                        .map(Amenity::getName)
                        .collect(Collectors.toList()),
                updated.getPhotos().stream()
                        .map(Photo::getUrl)
                        .collect(Collectors.toList())
        );
        try {
            String json = objectMapper.writeValueAsString(event);
            System.out.println(json);
            kafkaTemplate.send("accommodation-events", updated.getId().toString(), json);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize AccommodationEvent", e);
        }
    }


}
