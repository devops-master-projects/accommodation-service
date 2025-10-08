package org.example.accommodations.service;

import jakarta.persistence.EntityNotFoundException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.KafkaContainer;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import java.time.Duration;
import java.util.*;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

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
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);

    }

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Autowired
    private AccommodationService service;

    @Autowired
    private AmenityRepository amenityRepository;

    @Autowired
    private AccommodationRepository accommodationRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private UUID hostId;
    private Amenity wifi;
    private Amenity parking;


    @BeforeEach
    void setup() {
        accommodationRepository.deleteAll();
        amenityRepository.deleteAll();
        accommodationRepository.flush();
        amenityRepository.flush();
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
        var consumer = createKafkaConsumer();
        consumer.subscribe(List.of("accommodation-events"));

        consumer.poll(Duration.ofSeconds(1));
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId(), parking.getId()));
        UUID hostId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        AccommodationResponseDto created = service.create(dto, hostId);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getName()).isEqualTo("Seaside Apartment");
        assertThat(created.getLocation()).isNotNull();
        assertThat(created.getLocation().getCity()).isEqualTo("Novi Sad");
        assertThat(created.getUrlPhotos()).hasSize(2);
        assertThat(created.getAmenities())
                .extracting(AmenityResponseDto::getName)
                .containsExactlyInAnyOrder("WiFi", "Parking");

        Thread.sleep(1000);
        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        for (int i = 0; i < 5 && records.isEmpty(); i++) {
            records = consumer.poll(Duration.ofSeconds(1));
        }

        assertThat(records.count()).isGreaterThan(0);

        Optional<ConsumerRecord<String, String>> maybeRecord = StreamSupport.stream(
                records.records("accommodation-events").spliterator(), false
        ).filter(r -> r.key().equals(created.getId().toString())).findFirst();

        assertThat(maybeRecord).isPresent();

        String json = maybeRecord.get().value();
        AccommodationEvent event = objectMapper.readValue(json, AccommodationEvent.class);

        assertThat(event.getHostId()).isEqualTo(hostId.toString());
        assertThat(event.getName()).isEqualTo("Seaside Apartment");
        assertThat(event.getAmenities()).containsExactlyInAnyOrder("WiFi", "Parking");
        assertThat(event.getLocation().getCountry()).isEqualTo("RS");

        consumer.close();
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

        var consumer = createKafkaConsumer();
        consumer.subscribe(List.of("accommodation-events"));
        consumer.poll(Duration.ofSeconds(1));

        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())), hostId);
        UUID id = created.getId();

        for (int i = 0; i < 10; i++) {
            consumer.poll(Duration.ofSeconds(1));
            Thread.sleep(200);
        }

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

        AccommodationResponseDto updated = service.update(id, update, hostId);

        assertThat(updated.getName()).isEqualTo("Renovated Loft");
        assertThat(updated.getDescription()).isEqualTo("Freshly renovated");
        assertThat(updated.getLocation().getCity()).isEqualTo("Beograd");

        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        for (int i = 0; i < 10 && records.isEmpty(); i++) {
            Thread.sleep(500);
            records = consumer.poll(Duration.ofSeconds(1));
        }

        assertThat(records.count()).isGreaterThan(0);

        Optional<ConsumerRecord<String, String>> maybeRecord = StreamSupport.stream(
                        records.records("accommodation-events").spliterator(), false
                )
                .filter(r -> r.key().equals(id.toString()))
                .filter(r -> r.value().contains("Renovated Loft"))
                .findFirst();

        assertThat(maybeRecord).isPresent();

        String json = maybeRecord.get().value();
        AccommodationEvent event = objectMapper.readValue(json, AccommodationEvent.class);

        assertThat(event.getName()).isEqualTo("Renovated Loft");
        assertThat(event.getLocation().getCity()).isEqualTo("Beograd");
        assertThat(event.getAmenities()).containsExactly("Parking");
        assertThat(event.getPhotos()).containsExactly("https://img/x.jpg");

        consumer.close();
    }


    @Test
    @DisplayName("updateAutoConfirm(): flips flag and publishes AccommodationUpdated event")
    void testUpdateAutoConfirm() throws Exception {


        var consumer = createKafkaConsumer();
        consumer.subscribe(List.of("accommodation-events"));
        consumer.poll(Duration.ofSeconds(1));

        AccommodationResponseDto created = service.create(newCreateDto(List.of()), hostId);
        UUID id = created.getId();
        AccommodationResponseDto after = service.updateAutoConfirm(id, true, hostId);
        assertThat(after.getAutoConfirm()).isTrue();

        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        for (int i = 0; i < 5 && records.isEmpty(); i++) {
            records = consumer.poll(Duration.ofSeconds(1));
        }

        assertThat(records.count()).isGreaterThan(0);

        Optional<ConsumerRecord<String, String>> maybeRecord = StreamSupport.stream(
                records.records("accommodation-events").spliterator(), false
        ).filter(r -> r.key().equals(id.toString())).findFirst();

        assertThat(maybeRecord).isPresent();

        String json = maybeRecord.get().value();
        AccommodationEvent event = objectMapper.readValue(json, AccommodationEvent.class);

        assertThat(event.getHostId()).isEqualTo(hostId.toString());
        assertThat(event.getPhotos()).containsExactlyInAnyOrder("https://img/1.jpg", "https://img/2.jpg");
        assertThat(event.getAmenities()).isEmpty();

        consumer.close();
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
        UUID notExistingAmenityId = UUID.randomUUID();
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
        var consumer = createKafkaConsumer();
        consumer.subscribe(List.of("accommodation-events"));
        consumer.poll(Duration.ofSeconds(1));

        AccommodationResponseDto created = service.create(newCreateDto(List.of(wifi.getId())), hostId);
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        assertThat(records.count()).isGreaterThan(0);

        ConsumerRecord<String, String> record = records.iterator().next();
        assertThat(record.topic()).isEqualTo("accommodation-events");
        assertThat(record.key()).isEqualTo(created.getId().toString());

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

        ConsumerRecords<String, String> updatedRecords = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(5));
        boolean foundUpdate = StreamSupport.stream(
                updatedRecords.records("accommodation-events").spliterator(), false
        ).anyMatch(r -> r.key().equals(created.getId().toString()));


        assertThat(foundUpdate).isTrue();

        consumer.close();
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


    private Map<String, Object> kafkaConsumerProps(String groupId) {
        Map<String, Object> props = new HashMap<>();
        props.put("bootstrap.servers", kafka.getBootstrapServers());
        props.put("group.id", groupId);
        props.put("auto.offset.reset", "earliest");
        props.put("enable.auto.commit", "true");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        return props;
    }

    private Consumer<String, String> createKafkaConsumer() {
        String uniqueGroup = "test-group-" + UUID.randomUUID();
        DefaultKafkaConsumerFactory<String, String> factory =
                new DefaultKafkaConsumerFactory<>(kafkaConsumerProps(uniqueGroup));
        var consumer = factory.createConsumer();
        consumer.subscribe(List.of("accommodation-events"));
        return consumer;
    }




}
