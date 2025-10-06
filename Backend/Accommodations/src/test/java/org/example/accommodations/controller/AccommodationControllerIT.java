package org.example.accommodations.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.accommodations.dto.*;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(AccommodationControllerIT.TestSecurityConfig.class) // << uključi metodsku zaštitu i pravila
class AccommodationControllerIT {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean KafkaTemplate<String, String> kafkaTemplate;
    @MockitoBean JwtDecoder jwtDecoder;
    @Autowired AmenityRepository amenityRepo;

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurity(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers("/api/**").authenticated() // bez korisnika => 401
                    )
                    .httpBasic(org.springframework.security.config.Customizer.withDefaults())
                    .build();
        }
    }

    private AccommodationRequestDto req(String name, Set<UUID> amenityIds) {
        AccommodationRequestDto r = new AccommodationRequestDto();
        r.setHostId(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        r.setName(name);
        r.setDescription("desc");
        r.setMinGuests(1);
        r.setMaxGuests(3);
        r.setAutoConfirm(true);
        r.setPricingMode("PER_NIGHT");
        LocationDto l = new LocationDto();
        l.setCountry("RS"); l.setCity("Novi Sad"); l.setAddress("Bulevar 1"); l.setPostalCode("21000");
        r.setLocation(l);
        r.setPhotos(List.of("https://img/1.jpg"));
        r.setAmenities(amenityIds == null ? List.of() : amenityIds.stream().toList());
        return r;
    }

    @Test
    @WithMockUser(roles = "host")
    void post_then_get_roundtrip_with_h2_jpa() throws Exception {
        UUID wifiId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        amenityRepo.save(Amenity.builder().id(wifiId).name("WiFi").build());

        var create = req("Loft", Set.of(wifiId));

        var mvcCreate = mockMvc.perform(post("/api/accommodations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(create)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Loft"))
                .andReturn();

        var json = mvcCreate.getResponse().getContentAsString();
        var dto = objectMapper.readValue(json, AccommodationResponseDto.class);
        assertThat(dto.getId()).isNotNull();

        // GET by id (public endpoint ali security traži autentikaciju pa koristimo WithMockUser iznad metode)
        mockMvc.perform(get("/api/accommodations/{id}", dto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.name").value("Loft"));

        // GET all (takođe autentikovan jer je WithMockUser na metodi)
        mockMvc.perform(get("/api/accommodations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(dto.getId().toString()));

        // PATCH auto-confirm (host)
        mockMvc.perform(patch("/api/accommodations/{id}/auto-confirm", dto.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AutoConfirm(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(dto.getId().toString()))
                .andExpect(jsonPath("$.autoConfirm").value(false));

        // isti test, UNAUTHENTICATED poziv => 401 (bez @WithMockUser)
        mockMvc.perform(get("/api/accommodations/{id}/auto-confirm", dto.getId())
                        .with(anonymous()))
                .andExpect(status().isUnauthorized());
    }
}
