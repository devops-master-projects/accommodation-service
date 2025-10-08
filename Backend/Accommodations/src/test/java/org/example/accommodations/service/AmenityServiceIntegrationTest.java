package org.example.accommodations.service;

import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AmenityServiceIntegrationTest {

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
    private AmenityService service;

    @Autowired
    private AmenityRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("create(): persists amenity, generates ID and returns mapped DTO")
    void create_persistsAndMaps() {
        AmenityRequestDto req = AmenityRequestDto.builder()
                .name("WiFi")
                .description("High-speed internet")
                .build();

        AmenityResponseDto res = service.create(req);

        assertThat(res.getId()).isNotNull();
        assertThat(res.getName()).isEqualTo("WiFi");
        assertThat(res.getDescription()).isEqualTo("High-speed internet");

        // verify in DB
        Optional<Amenity> inDb = repository.findById(res.getId());
        assertThat(inDb).isPresent();
        assertThat(inDb.get().getName()).isEqualTo("WiFi");
        assertThat(inDb.get().getDescription()).isEqualTo("High-speed internet");
    }

    @Test
    @DisplayName("create(): throws IllegalArgumentException when name already exists")
    void create_duplicateName_throws() {
        // seed one
        AmenityRequestDto first = AmenityRequestDto.builder()
                .name("Parking")
                .description("On-site parking")
                .build();
        AmenityResponseDto a = service.create(first);
        assertThat(a.getId()).isNotNull();

        // duplicate
        AmenityRequestDto dup = AmenityRequestDto.builder()
                .name("Parking")
                .description("Another description")
                .build();

        assertThatThrownBy(() -> service.create(dup))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        // still only one in DB with that name
        List<Amenity> all = repository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getName()).isEqualTo("Parking");
    }

    @Test
    @DisplayName("create(): fails immediately when name is null (DB not-null constraint)")
    void create_nullName_failsOnFlush() {
        AmenityRequestDto req = AmenityRequestDto.builder()
                .name(null)
                .description("desc")
                .build();

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(DataIntegrityViolationException.class);
    }


    @Test
    @DisplayName("create(): description is optional (can be null)")
    void create_descriptionOptional() {
        AmenityRequestDto req = AmenityRequestDto.builder()
                .name("Air Conditioning")
                .description(null)
                .build();

        AmenityResponseDto res = service.create(req);
        assertThat(res.getId()).isNotNull();
        assertThat(res.getName()).isEqualTo("Air Conditioning");
        assertThat(res.getDescription()).isNull();

        Amenity db = repository.findById(res.getId()).orElseThrow();
        assertThat(db.getDescription()).isNull();
    }

    @Test
    @DisplayName("getAll(): returns empty list when there are no amenities")
    void getAll_empty() {
        ResponseEntity<List<AmenityResponseDto>> resp = service.getAll();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("getAll(): returns OK and all amenities mapped to DTO")
    void getAll_returnsAll() {
        // seed
        service.create(AmenityRequestDto.builder().name("WiFi").description("High-speed internet").build());
        service.create(AmenityRequestDto.builder().name("Parking").description("On-site").build());

        ResponseEntity<List<AmenityResponseDto>> resp = service.getAll();
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        List<AmenityResponseDto> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).hasSize(2);

        assertThat(body)
                .extracting(AmenityResponseDto::getName)
                .containsExactlyInAnyOrder("WiFi", "Parking");
    }
}
