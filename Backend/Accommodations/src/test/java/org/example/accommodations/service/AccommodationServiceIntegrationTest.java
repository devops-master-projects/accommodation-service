package org.example.accommodations.service;

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Assertions;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.LocationDto;
import org.example.accommodations.dto.AccommodationEvent;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.example.accommodations.repository.AccommodationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AccommodationServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("accommodations_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
    }

    @Autowired
    private AccommodationService service;

    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID hostId;
    private Amenity wifi;
    private Amenity parking;




    @BeforeEach
    void setup() {
        accommodationRepository.deleteAll();
        amenityRepository.deleteAll();

        hostId = UUID.randomUUID();
        wifi = amenityRepository.save(Amenity.builder().name("WiFi").build());
        parking = amenityRepository.save(Amenity.builder().name("Parking").build());
    }

    private AccommodationRequestDto newCreateDto(List<UUID> amenityIds) {
        return AccommodationRequestDto.builder()
                .name("Seaside Apartment")
                .description("Nice view, close to beach")
                .minGuests(1)
                .maxGuests(4)
                .autoConfirm(false)
                .pricingMode("PER_NIGHT")
                .location(LocationDto.builder()
                        .country("RS")
                        .city("Novi Sad")
                        .address("Kej 1")
                        .postalCode("21000")
                        .build())
                .photos(List.of(
                        "https://img/1.jpg",
                        "https://img/2.jpg"
                ))
                .amenities(amenityIds)
                .build();
    }

    @Test
    @DisplayName("create(): persist entity with full details and publishes AccommodationCreated event")
    void testCreate() throws Exception {
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId(), parking.getId()));

        AccommodationResponseDto created = service.create(dto, UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Seaside Apartment");
        assertThat(created.getLocation()).isNotNull();
        assertThat(created.getLocation().getCity()).isEqualTo("Novi Sad");
        assertThat(created.getUrlPhotos()).hasSize(2);
        assertThat(created.getAmenities())
                .extracting(AmenityResponseDto::getName)
                .containsExactlyInAnyOrder("WiFi", "Parking");

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, times(1)).send(topicCap.capture(), keyCap.capture(), valueCap.capture());

        assertThat(topicCap.getValue()).isEqualTo("accommodation-events");
        assertThat(keyCap.getValue()).isEqualTo(created.getId().toString());

        AccommodationEvent event = objectMapper.readValue(valueCap.getValue(), AccommodationEvent.class);
        assertThat(event.getHostId()).isEqualTo("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        assertThat(event.getName()).isEqualTo("Seaside Apartment");
        assertThat(event.getAmenities()).containsExactlyInAnyOrder("WiFi", "Parking");
        assertThat(event.getLocation().getCountry()).isEqualTo("RS");
    }

    @Test
    @DisplayName("getById(): returns DTO with details")
    void testGetById() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())), UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));

        AccommodationResponseDto found = service.getById(created.getId());
        assertThat(found.getId()).isEqualTo(created.getId());
        assertThat(found.getUrlPhotos()).hasSize(2);
        assertThat(found.getAmenities())
                .extracting(AmenityResponseDto::getName)
                .containsExactly("WiFi");
    }

    @Test
    @DisplayName("getAll(): returns all items")
    void testGetAll() {
        service.create(newCreateDto(List.of(wifi.getId())),hostId);
        service.create(newCreateDto(List.of(parking.getId())),hostId);
        List<AccommodationResponseDto> all = service.getAll();
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("update(): updates fields, replaces photos & amenities and publishes AccommodationUpdated event")
    void testUpdate() throws Exception {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())), hostId);
        UUID id = created.getId();

        AccommodationRequestDto update = AccommodationRequestDto.builder()
                .name("Renovated Loft")
                .description("Freshly renovated")
                .minGuests(2)
                .maxGuests(5)
                .autoConfirm(true)
                .pricingMode("PER_NIGHT")
                .location(LocationDto.builder()
                        .country("RS")
                        .city("Beograd")
                        .address("Knez Mihailova 1")
                        .postalCode("11000")
                        .build())
                .photos(List.of("https://img/x.jpg"))
                .amenities(List.of(parking.getId()))
                .build();

        AccommodationResponseDto updated = service.update(id, update,hostId);

        assertThat(updated.getName()).isEqualTo("Renovated Loft");
        assertThat(updated.getDescription()).isEqualTo("Freshly renovated");
        assertThat(updated.getMinGuests()).isEqualTo(2);
        assertThat(updated.getMaxGuests()).isEqualTo(5);
        assertThat(updated.getAutoConfirm()).isTrue();
        assertThat(updated.getLocation().getCity()).isEqualTo("Beograd");
        assertThat(updated.getAmenities())
                .extracting(AmenityResponseDto::getName)
                .containsExactly("Parking");

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);

        verify(kafkaTemplate, atLeast(2)).send(topicCap.capture(), keyCap.capture(), valueCap.capture());
        String lastJson = valueCap.getAllValues().get(valueCap.getAllValues().size() - 1);
        AccommodationEvent event = objectMapper.readValue(lastJson, AccommodationEvent.class);
        assertThat(event.getName()).isEqualTo("Renovated Loft");
        assertThat(event.getLocation().getCity()).isEqualTo("Beograd");
        assertThat(event.getAmenities()).containsExactly("Parking");
        assertThat(event.getPhotos()).containsExactly("https://img/x.jpg");
    }

    @Test
    @DisplayName("updateAutoConfirm(): flips flag and publishes AccommodationUpdated event")
    void testUpdateAutoConfirm() throws Exception {
        AccommodationResponseDto created = service.create(newCreateDto(List.of()),hostId);
        UUID id = created.getId();

        AccommodationResponseDto after = service.updateAutoConfirm(id, true, hostId);
        assertThat(after.getAutoConfirm()).isTrue();

        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeast(2)).send(anyString(), anyString(), valueCap.capture());
        String last = valueCap.getAllValues().get(valueCap.getAllValues().size() - 1);
        AccommodationEvent ev = objectMapper.readValue(
                valueCap.getAllValues().get(valueCap.getAllValues().size() - 1),
                AccommodationEvent.class
        );
        assertThat(ev.getAmenities()).isEmpty();
        assertThat(ev.getPhotos()).contains("https://img/2.jpg", "https://img/1.jpg");

    }

    @Test
    @DisplayName("getAutoConfirm(): returns current value")
    void testGetAutoConfirm() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of()), hostId);
        UUID id = created.getId();
        assertThat(service.getAutoConfirm(id)).isFalse();

        service.updateAutoConfirm(id, true,hostId);
        assertThat(service.getAutoConfirm(id)).isTrue();
    }

    @Test
    @DisplayName("getAll(): returns empty list when database is empty")
    void testGetAllEmpty() {
        List<AccommodationResponseDto> all = service.getAll();
        assertThat(all).isEmpty();
    }

    @Test
    @DisplayName("getById(): throws EntityNotFoundException when accommodation does not exist")
    void testGetByIdNotFound() {
        UUID randomId = UUID.randomUUID();
        Assertions.assertThrows(EntityNotFoundException.class, () -> service.getById(randomId));
    }

    @Test
    @DisplayName("update(): throws EntityNotFoundException when accommodation does not exist")
    void testUpdateNotFound() {
        UUID randomId = UUID.randomUUID();

        AccommodationRequestDto update = AccommodationRequestDto.builder()
                .name("Name")
                .description("Desc")
                .minGuests(1)
                .maxGuests(2)
                .autoConfirm(true)
                .pricingMode("PER_NIGHT")
                .location(LocationDto.builder()
                        .country("RS").city("Beograd").address("Addr").postalCode("11000").build())
                .photos(List.of("https://img/x.jpg"))
                .amenities(List.of(parking.getId()))
                .build();

        Assertions.assertThrows(EntityNotFoundException.class, () -> service.update(randomId, update,hostId));
    }

    @Test
    @DisplayName("updateAutoConfirm(): throws EntityNotFoundException when accommodation does not exist")
    void testUpdateAutoConfirmNotFound() {
        UUID randomId = UUID.randomUUID();
        Assertions.assertThrows(EntityNotFoundException.class, () -> service.updateAutoConfirm(randomId, true,hostId));
    }

    @Test
    @DisplayName("create(): throws IllegalArgumentException when any amenity does not exist")
    void testCreateWithMissingAmenityThrows() {
        UUID notExistingAmenityId = UUID.randomUUID(); // not in repo
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId(), notExistingAmenityId));
        Assertions.assertThrows(IllegalArgumentException.class, () -> service.create(dto, hostId));
    }

    @Test
    @DisplayName("update(): ignores non-existing amenity IDs and keeps only existing ones")
    void testUpdateIgnoresMissingAmenityIds() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())), hostId);
        UUID id = created.getId();

        UUID missing = UUID.randomUUID();
        AccommodationRequestDto update = AccommodationRequestDto.builder()
                .name("Same Name")
                .description("Same Desc")
                .minGuests(1)
                .maxGuests(4)
                .autoConfirm(false)
                .pricingMode("PER_NIGHT")
                .location(LocationDto.builder()
                        .country("RS").city("Novi Sad").address("Kej 1").postalCode("21000").build())
                .photos(List.of("https://img/1.jpg", "https://img/2.jpg"))
                .amenities(List.of(parking.getId(), missing))
                .build();

        AccommodationResponseDto updated = service.update(id, update,hostId);

        assertThat(updated.getAmenities())
                .extracting(AmenityResponseDto::getName)
                .containsExactly("Parking");
    }

    @Test
    @DisplayName("update(): fully replaces photos (previous photos are cleared)")
    void testUpdateReplacesPhotosCompletely() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())),hostId);
        UUID id = created.getId();
        assertThat(created.getUrlPhotos()).containsExactlyInAnyOrder("https://img/1.jpg", "https://img/2.jpg");

        AccommodationRequestDto update = AccommodationRequestDto.builder()
                .name(created.getName())
                .description(created.getDescription())
                .minGuests(created.getMinGuests())
                .maxGuests(created.getMaxGuests())
                .autoConfirm(created.getAutoConfirm())
                .pricingMode("PER_NIGHT")
                .location(created.getLocation())
                .photos(List.of("https://img/new.jpg")) // replace
                .amenities(List.of(wifi.getId()))
                .build();

        AccommodationResponseDto after = service.update(id, update, hostId);
        assertThat(after.getUrlPhotos()).containsExactly("https://img/new.jpg");
    }

    @Test
    @DisplayName("Kafka: topic is 'accommodation-events' and key equals accommodationId for create and update")
    void testKafkaTopicAndKey() throws Exception {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())),hostId);

        ArgumentCaptor<String> topicCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeastOnce()).send(topicCap.capture(), keyCap.capture(), valueCap.capture());

        assertThat(topicCap.getAllValues().get(0)).isEqualTo("accommodation-events");
        assertThat(keyCap.getAllValues().get(0)).isEqualTo(created.getId().toString());

        AccommodationRequestDto update = AccommodationRequestDto.builder()
                .name("Changed Name")
                .description(created.getDescription())
                .minGuests(created.getMinGuests())
                .maxGuests(created.getMaxGuests())
                .autoConfirm(created.getAutoConfirm())
                .pricingMode(created.getPricingMode())
                .location(created.getLocation())
                .photos(created.getUrlPhotos())
                .amenities(List.of(wifi.getId()))
                .build();

        service.update(created.getId(), update, hostId);

        verify(kafkaTemplate, atLeast(2)).send(topicCap.capture(), keyCap.capture(), valueCap.capture());
        String lastTopic = topicCap.getAllValues().get(topicCap.getAllValues().size() - 1);
        String lastKey   = keyCap.getAllValues().get(keyCap.getAllValues().size() - 1);
        assertThat(lastTopic).isEqualTo("accommodation-events");
        assertThat(lastKey).isEqualTo(created.getId().toString());
    }

    @Test
    @DisplayName("updateAutoConfirm(): toggling flag keeps name, location and photos unchanged")
    void testUpdateAutoConfirmKeepsOtherFields() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())),hostId);
        UUID id = created.getId();

        AccommodationResponseDto after = service.updateAutoConfirm(id, true, hostId);
        assertThat(after.getAutoConfirm()).isTrue();

        AccommodationResponseDto reloaded = service.getById(id);
        assertThat(reloaded.getName()).isEqualTo(created.getName());
        assertThat(reloaded.getLocation().getCity()).isEqualTo(created.getLocation().getCity());
        assertThat(reloaded.getUrlPhotos()).containsExactlyInAnyOrderElementsOf(created.getUrlPhotos());
    }

    @Test
    @DisplayName("create(): pricingMode is a string and preserved in response")
    void testCreatePricingModeStringPreserved() {
        AccommodationResponseDto created = service.create(newCreateDto(List.of()), hostId);
        assertThat(created.getPricingMode()).isEqualTo("PER_NIGHT");
    }
}
