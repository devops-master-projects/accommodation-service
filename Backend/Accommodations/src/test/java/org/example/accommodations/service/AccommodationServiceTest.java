package org.example.accommodations.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.LocationDto;
import org.example.accommodations.mappers.AccommodationMapper;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.model.Location;
import org.example.accommodations.model.Photo;
import org.example.accommodations.repository.AccommodationRepository;
import org.example.accommodations.repository.AmenityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccommodationServiceTest {

    @Mock private AccommodationRepository accommodationRepository;
    @Mock private AccommodationMapper accommodationMapper;
    @Mock private AmenityRepository amenityRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private AccommodationService accommodationService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        accommodationService = new AccommodationService(
                accommodationRepository,
                accommodationMapper,
                amenityRepository,
                kafkaTemplate,
                objectMapper
        );
    }

    private Accommodation makeAccommodation(UUID id) {
        Location loc = Location.builder()
                .country("RS").city("Novi Sad").address("Bulevar 1").postalCode("21000")
                .build();

        Amenity wifi = Amenity.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .name("WiFi")
                .build();

        Photo p1 = Photo.builder().url("https://img/1.jpg").build();

        Accommodation a = Accommodation.builder()
                .id(id)
                .hostId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .name("Sunny Apt")
                .description("Nice place")
                .minGuests(1)
                .maxGuests(4)
                .autoConfirm(true)
                .pricingMode("PER_NIGHT")
                .location(loc)
                .photos(new HashSet<>(List.of(p1)))
                .amenities(new HashSet<>(List.of(wifi)))
                .build();

        p1.setAccommodation(a);
        return a;
    }

    private AccommodationRequestDto makeRequestDto(Collection<UUID> amenityIds) {
        AccommodationRequestDto dto = new AccommodationRequestDto();
        dto.setName("Cozy Loft");
        dto.setDescription("Updated desc");
        dto.setMinGuests(2);
        dto.setMaxGuests(3);
        dto.setAutoConfirm(false);
        dto.setPricingMode("PER_GUEST");

        LocationDto l = new LocationDto();
        l.setCountry("RS");
        l.setCity("Beograd");
        l.setAddress("Knez Mihajlova 10");
        l.setPostalCode("11000");
        dto.setLocation(l);

        dto.setPhotos(List.of("https://img/2.jpg", "https://img/3.jpg"));
        dto.setAmenities(amenityIds == null ? null : amenityIds.stream().toList());
        return dto;
    }

    private static class SavedHolder<T> { T value; }

    // getById

    @Test
    void getById_returnsDto_whenFound() {
        UUID id = UUID.randomUUID();
        Accommodation entity = makeAccommodation(id);
        AccommodationResponseDto dto = new AccommodationResponseDto();

        given(accommodationRepository.findByIdWithDetails(id)).willReturn(Optional.of(entity));
        given(accommodationMapper.toDto(entity)).willReturn(dto);

        AccommodationResponseDto result = accommodationService.getById(id);

        assertThat(result).isSameAs(dto);
        verify(accommodationRepository).findByIdWithDetails(id);
        verify(accommodationMapper).toDto(entity);
    }

    @Test
    void getById_throws_whenMissing() {
        UUID id = UUID.randomUUID();
        given(accommodationRepository.findByIdWithDetails(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accommodationService.getById(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // getAll

    @Test
    void getAll_mapsAll() {
        Accommodation a1 = makeAccommodation(UUID.randomUUID());
        Accommodation a2 = makeAccommodation(UUID.randomUUID());
        given(accommodationRepository.findAllWithDetails()).willReturn(List.of(a1, a2));
        AccommodationResponseDto d1 = new AccommodationResponseDto();
        AccommodationResponseDto d2 = new AccommodationResponseDto();
        given(accommodationMapper.toDto(a1)).willReturn(d1);
        given(accommodationMapper.toDto(a2)).willReturn(d2);

        List<AccommodationResponseDto> result = accommodationService.getAll();

        assertThat(result).containsExactly(d1, d2);
        verify(accommodationRepository).findAllWithDetails();
        verify(accommodationMapper).toDto(a1);
        verify(accommodationMapper).toDto(a2);
    }

    @Test
    void getAll_whenEmpty_returnsEmptyList() {
        given(accommodationRepository.findAllWithDetails()).willReturn(List.of());

        List<AccommodationResponseDto> result = accommodationService.getAll();

        assertThat(result).isEmpty();
        verify(accommodationRepository).findAllWithDetails();
        verifyNoInteractions(accommodationMapper);
    }

    // updateAutoConfirm / getAutoConfirm

    @Test
    void updateAutoConfirm_updates_and_sendsEvent() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        a.setAutoConfirm(false);

        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));
        given(accommodationRepository.save(a)).willAnswer(inv -> inv.getArgument(0));
        AccommodationResponseDto dto = new AccommodationResponseDto();
        given(accommodationMapper.toDto(a)).willReturn(dto);

        AccommodationResponseDto res = accommodationService.updateAutoConfirm(id, true, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(a.getAutoConfirm()).isTrue();
        assertThat(res).isSameAs(dto);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(topic.capture(), key.capture(), payload.capture());

        assertThat(topic.getValue()).isEqualTo("accommodation-events");
        assertThat(key.getValue()).isEqualTo(id.toString());
        assertThat(payload.getValue()).contains("\"eventType\":\"AccommodationUpdated\"");
    }

    @Test
    void updateAutoConfirm_throws_whenMissing() {
        UUID id = UUID.randomUUID();
        given(accommodationRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accommodationService.updateAutoConfirm(id, true, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(accommodationRepository).findById(id);
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void getAutoConfirm_returnsFlag() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        a.setAutoConfirm(true);
        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));

        boolean flag = accommodationService.getAutoConfirm(id);

        assertThat(flag).isTrue();
    }

    @Test
    void getAutoConfirm_throws_whenMissing() {
        UUID id = UUID.randomUUID();
        given(accommodationRepository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> accommodationService.getAutoConfirm(id))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    // update

    @Test
    void update_overwrites_fields_photos_amenities_and_sendsEvent() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));

        UUID amenity1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID amenity2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

        Amenity am10 = Amenity.builder().id(amenity1).name("Parking").build();
        Amenity am11 = Amenity.builder().id(amenity2).name("AC").build();

        given(amenityRepository.findById(amenity1)).willReturn(Optional.of(am10));
        given(amenityRepository.findById(amenity2)).willReturn(Optional.of(am11));
        given(accommodationRepository.save(any(Accommodation.class)))
                .willAnswer(inv -> inv.getArgument(0));

        AccommodationResponseDto dto = new AccommodationResponseDto();
        given(accommodationMapper.toDto(any(Accommodation.class))).willReturn(dto);

        AccommodationRequestDto req = makeRequestDto(Set.of(amenity1, amenity2));

        AccommodationResponseDto out = accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(out).isSameAs(dto);
        // ... (ostale asercije ostaju iste)

        verify(kafkaTemplate).send(eq("accommodation-events"), eq(id.toString()), argThat(json ->
                json.contains("\"eventType\":\"AccommodationUpdated\"") &&
                        json.contains("\"name\":\"Cozy Loft\"") &&
                        json.contains("\"city\":\"Beograd\"")
        ));
    }

    @Test
    void update_ignores_missing_amenities_silently() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));

        UUID amenity1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID amenity2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

        Amenity am10 = Amenity.builder().id(amenity1).name("Parking").build();
        given(amenityRepository.findById(amenity1)).willReturn(Optional.of(am10));
        given(amenityRepository.findById(amenity2)).willReturn(Optional.empty());

        given(accommodationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(Set.of(amenity1, amenity2));

        AccommodationResponseDto out = accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(out).isNotNull();
        assertThat(a.getAmenities().stream().map(Amenity::getId)).containsExactly(amenity1);
    }

    @Test
    void update_whenPhotosNull_clearsExistingPhotos() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        assertThat(a.getPhotos()).isNotEmpty();

        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));
        given(accommodationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(Set.of()); // start from base
        req.setPhotos(null); // simulate null photos

        accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(a.getPhotos()).isEmpty(); // should be cleared
    }

    @Test
    void update_whenAmenitiesNull_setsEmptyAmenities() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        assertThat(a.getAmenities()).isNotEmpty();

        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));
        given(accommodationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(null); // amenities null

        accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(a.getAmenities()).isEmpty();
        verifyNoInteractions(amenityRepository);
    }

    @Test
    void update_whenLocationWasNull_createsNewLocation() {
        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        a.setLocation(null); // simulate missing location

        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));
        given(accommodationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(Set.of());

        accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(a.getLocation()).isNotNull();
        assertThat(a.getLocation().getCity()).isEqualTo("Beograd");
    }

    @Test
    void update_throws_whenAccommodationMissing() {
        UUID id = UUID.randomUUID();
        given(accommodationRepository.findById(id)).willReturn(Optional.empty());

        AccommodationRequestDto req = makeRequestDto(Set.of());

        assertThatThrownBy(() -> accommodationService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining(id.toString());

        verify(accommodationRepository).findById(id);
        verifyNoInteractions(accommodationMapper, kafkaTemplate);
    }

    @Test
    void update_bubblesRuntime_whenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        given(failingMapper.writeValueAsString(any())).willThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        AccommodationService failingService = new AccommodationService(
                accommodationRepository, accommodationMapper, amenityRepository, kafkaTemplate, failingMapper
        );

        UUID id = UUID.randomUUID();
        Accommodation a = makeAccommodation(id);
        given(accommodationRepository.findById(id)).willReturn(Optional.of(a));
        given(accommodationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // Ne stubujemo mapper.toDto(...) — exception se dešava pre mapiranja
        AccommodationRequestDto req = makeRequestDto(Set.of());

        assertThatThrownBy(() -> failingService.update(id, req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize AccommodationEvent");

        verifyNoInteractions(kafkaTemplate);
    }

    // create

    @Test
    void create_builds_entity_resolves_amenities_sends_created_event_and_maps() {
        UUID amenity1 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        UUID amenity2 = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2");

        Amenity am10 = Amenity.builder().id(amenity1).name("Parking").build();
        Amenity am11 = Amenity.builder().id(amenity2).name("AC").build();
        given(amenityRepository.findById(amenity1)).willReturn(Optional.of(am10));
        given(amenityRepository.findById(amenity2)).willReturn(Optional.of(am11));

        SavedHolder<Accommodation> saved = new SavedHolder<>();
        given(accommodationRepository.save(any(Accommodation.class))).willAnswer(inv -> {
            Accommodation a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
            }
            a.getPhotos().forEach(p -> p.setAccommodation(a));
            saved.value = a;
            return a;
        });

        AccommodationResponseDto dto = new AccommodationResponseDto();
        given(accommodationMapper.toDto(any(Accommodation.class))).willReturn(dto);

        AccommodationResponseDto out = accommodationService.create(makeRequestDto(Set.of(amenity1, amenity2)), UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(out).isSameAs(dto);
        Accommodation persisted = saved.value;
        assertThat(persisted).isNotNull();
        // ... (ostale asercije ostaju iste)

        verify(kafkaTemplate).send(eq("accommodation-events"),
                eq(persisted.getId().toString()),
                argThat(json -> json.contains("\"eventType\":\"AccommodationCreated\"")));
    }

    @Test
    void create_throws_when_amenity_missing() {
        AccommodationRequestDto req = makeRequestDto(Set.of(
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1"),
                UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa2")
        ));
        given(amenityRepository.findById(any(UUID.class))).willReturn(Optional.empty());

        assertThatThrownBy(() -> accommodationService.create(req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amenity not found");

        verify(accommodationRepository, never()).save(any());
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    void create_whenPhotosNull_isAllowed_createsEmptyPhotosSet() {
        UUID am = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        given(amenityRepository.findById(am)).willReturn(Optional.of(Amenity.builder().id(am).name("WiFi").build()));

        SavedHolder<Accommodation> saved = new SavedHolder<>();
        given(accommodationRepository.save(any(Accommodation.class))).willAnswer(inv -> {
            Accommodation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            saved.value = a;
            return a;
        });
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(Set.of(am));
        req.setPhotos(null); // null photos

        accommodationService.create(req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(saved.value.getPhotos()).isEmpty();
        verify(kafkaTemplate).send(eq("accommodation-events"), anyString(), anyString());
    }

    @Test
    void create_whenAmenitiesNull_isAllowed_createsEmptyAmenitySet() {
        SavedHolder<Accommodation> saved = new SavedHolder<>();
        given(accommodationRepository.save(any(Accommodation.class))).willAnswer(inv -> {
            Accommodation a = inv.getArgument(0);
            a.setId(UUID.randomUUID());
            saved.value = a;
            return a;
        });
        given(accommodationMapper.toDto(any())).willReturn(new AccommodationResponseDto());

        AccommodationRequestDto req = makeRequestDto(null); // amenities null

        accommodationService.create(req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(saved.value.getAmenities()).isEmpty();
        verifyNoInteractions(amenityRepository);
        verify(kafkaTemplate).send(eq("accommodation-events"), anyString(), anyString());
    }

    @Test
    void create_bubblesRuntime_whenSerializationFails() throws Exception {
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        given(failingMapper.writeValueAsString(any())).willThrow(new com.fasterxml.jackson.core.JsonProcessingException("boom") {});
        AccommodationService failingService = new AccommodationService(
                accommodationRepository, accommodationMapper, amenityRepository, kafkaTemplate, failingMapper
        );

        UUID am = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa1");
        given(amenityRepository.findById(am)).willReturn(Optional.of(Amenity.builder().id(am).name("WiFi").build()));
        given(accommodationRepository.save(any(Accommodation.class))).willAnswer(inv -> {
            Accommodation a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
            return a;
        });

        AccommodationRequestDto req = makeRequestDto(Set.of(am));

        assertThatThrownBy(() -> failingService.create(req, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to serialize AccommodationEvent");

        verifyNoInteractions(kafkaTemplate);
    }

}
