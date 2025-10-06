package org.example.accommodations.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.LocationDto;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.example.accommodations.repository.AccommodationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccommodationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Autowired private AmenityRepository amenityRepository;
    @Autowired private AccommodationRepository accommodationRepository;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    private Amenity wifi;
    private Amenity parking;
    private UUID hostId;

    @BeforeEach
    void setUp() {
        hostId = UUID.randomUUID();
        wifi = amenityRepository.save(Amenity.builder().name("WiFi").description("fast").build());
        parking = amenityRepository.save(Amenity.builder().name("Parking").description("on-site").build());
    }

    private AccommodationRequestDto newCreateDto(List<UUID> amenityIds) {
        AccommodationRequestDto dto = new AccommodationRequestDto();
        dto.setName("Seaside Apartment");
        dto.setDescription("Nice view, close to beach");
        dto.setMinGuests(1);
        dto.setMaxGuests(4);
        dto.setAutoConfirm(false);
        dto.setPricingMode("PER_NIGHT");

        LocationDto loc = new LocationDto();
        loc.setCountry("RS");
        loc.setCity("Novi Sad");
        loc.setAddress("Kej 1");
        loc.setPostalCode("21000");
        dto.setLocation(loc);

        dto.setPhotos(List.of("https://img/1.jpg", "https://img/2.jpg"));
        dto.setAmenities(amenityIds);
        return dto;
    }

    @Test
    @DisplayName("GET /api/accommodations returns 200 and empty list when there are no items")
    void getAll_empty() throws Exception {
        mockMvc.perform(get("/api/accommodations")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host")))) // ← dodato
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("POST /api/accommodations without authentication returns 401")
    void create_unauthenticated_returns401() throws Exception {
        var dto = newCreateDto(List.of(wifi.getId()));

        mockMvc.perform(post("/api/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isUnauthorized()) // 401
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")));
    }

    @Test
    @WithMockUser(roles = "guest")
    @DisplayName("POST /api/accommodations with role guest is forbidden (403)")
    void create_guestForbidden_returns403() throws Exception {
        var dto = newCreateDto(List.of(wifi.getId()));

        mockMvc.perform(post("/api/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden()); // 403
    }

    @Test
    @DisplayName("POST /api/accommodations with role host creates and returns DTO")
    void create_okWithHostRole() throws Exception {
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId(), parking.getId()));

        mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Seaside Apartment"));
    }


    @Test
    @DisplayName("GET /api/accommodations returns 200 and list with created item")
    void getAll_afterCreate() throws Exception {
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId()));

        mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Seaside Apartment"));
    }


    @Test
    @DisplayName("GET /api/accommodations/{id} returns 200 and the DTO")
    void getById_ok() throws Exception {
        String body = mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateDto(List.of(wifi.getId())))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AccommodationResponseDto created = objectMapper.readValue(body, AccommodationResponseDto.class);

        mockMvc.perform(get("/api/accommodations/{id}", created.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(created.getId().toString()))
                .andExpect(jsonPath("$.amenities[*].name", contains("WiFi")))
                .andExpect(jsonPath("$.urlPhotos", hasSize(2)));
    }

    @Test
    @DisplayName("PUT /api/accommodations/{id} updates fields, photos and amenities")
    void update_ok() throws Exception {
        String createdJson = mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateDto(List.of(wifi.getId())))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AccommodationResponseDto created = objectMapper.readValue(createdJson, AccommodationResponseDto.class);

        AccommodationRequestDto upd = new AccommodationRequestDto();
        upd.setName("Renovated Loft");
        upd.setDescription("Freshly renovated");
        upd.setMinGuests(2);
        upd.setMaxGuests(5);
        upd.setAutoConfirm(true);
        upd.setPricingMode("PER_NIGHT");
        LocationDto loc = new LocationDto();
        loc.setCountry("RS");
        loc.setCity("Beograd");
        loc.setAddress("Knez Mihailova 1");
        loc.setPostalCode("11000");
        upd.setLocation(loc);
        upd.setPhotos(List.of("https://img/x.jpg"));
        upd.setAmenities(List.of(parking.getId()));

        mockMvc.perform(put("/api/accommodations/{id}", created.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(upd)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renovated Loft"))
                .andExpect(jsonPath("$.location.city").value("Beograd"))
                .andExpect(jsonPath("$.urlPhotos", contains("https://img/x.jpg")))
                .andExpect(jsonPath("$.amenities[*].name", contains("Parking")));

        ArgumentCaptor<String> valueCap = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate, atLeastOnce()).send(eq("accommodation-events"),
                anyString(), valueCap.capture());
        assertThat(valueCap.getAllValues().stream().anyMatch(v -> v.contains("AccommodationUpdated"))).isTrue();
    }

    @Test
    @DisplayName("PATCH /api/accommodations/{id}/auto-confirm toggles flag and returns updated DTO")
    void patch_autoConfirm_ok() throws Exception {
        String createdJson = mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateDto(List.of()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        AccommodationResponseDto created = objectMapper.readValue(createdJson, AccommodationResponseDto.class);

        mockMvc.perform(patch("/api/accommodations/{id}/auto-confirm", created.getId())
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoConfirm\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoConfirm").value(true));
    }


    @Test
    @DisplayName("GET /api/accommodations/{id}/auto-confirm is allowed to role guest and returns current value")
    void get_autoConfirm_guestAllowed() throws Exception {
        var createBody = objectMapper.writeValueAsString(newCreateDto(List.of(wifi.getId())));

        var createRes = mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1")) // host UUID
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var created = objectMapper.readValue(createRes, org.example.accommodations.dto.AccommodationResponseDto.class);
        var id = created.getId();

        mockMvc.perform(get("/api/accommodations/{id}/auto-confirm", id)
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "7f5a8623-1122-49c1-b7c9-1234abcd5678")) // guest UUID
                                .authorities(new SimpleGrantedAuthority("ROLE_guest"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.autoConfirm").value(created.getAutoConfirm()));
    }

    @Test
    @DisplayName("GET /api/accommodations/{id}/auto-confirm without authentication returns 401")
    void get_autoConfirm_unauthenticated_returns401() throws Exception {
        var createRes = mockMvc.perform(post("/api/accommodations")
                        .with(jwt()
                                .jwt(jwt -> jwt.claim("sub", "4e9a3623-4424-4bba-b7b7-354fa6f753b1"))
                                .authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newCreateDto(List.of()))))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var created = objectMapper.readValue(createRes, org.example.accommodations.dto.AccommodationResponseDto.class);

        mockMvc.perform(get("/api/accommodations/{id}/auto-confirm", created.getId()))
                .andExpect(status().isUnauthorized());
    }


    @Test
    @WithMockUser(roles = "guest")
    @DisplayName("Security: POST /api/accommodations is forbidden to role guest")
    void create_forbiddenToGuest() throws Exception {
        AccommodationRequestDto dto = newCreateDto(List.of(wifi.getId()));

        mockMvc.perform(post("/api/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security: GET /api/accommodations without authentication returns 401")
    void getAll_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/accommodations"))
                .andExpect(status().isUnauthorized());
    }


}
