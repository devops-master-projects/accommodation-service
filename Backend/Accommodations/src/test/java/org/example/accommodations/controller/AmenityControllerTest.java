package org.example.accommodations.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AmenityControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AmenityRepository amenityRepository;

    @BeforeEach
    void cleanDb() {
        amenityRepository.deleteAll();
    }

    private AmenityRequestDto req(String name, String desc) {
        return AmenityRequestDto.builder()
                .name(name)
                .description(desc)
                .build();
    }

    @Test
    @DisplayName("GET /api/amenities returns 200 and empty list when no amenities exist")
    void getAll_empty_ok() throws Exception {
        mockMvc.perform(get("/api/amenities"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @DisplayName("POST /api/amenities without JWT returns 401")
    void create_unauthenticated_401() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("WiFi", "fast"))))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Bearer")));
    }

    @Test
    @DisplayName("POST /api/amenities with JWT role guest returns 403")
    void create_guest_403() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_guest")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("WiFi", "fast"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/amenities with JWT role host returns 200 and persists amenity")
    void create_host_ok_persists() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("WiFi", "High-speed internet"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("WiFi"))
                .andExpect(jsonPath("$.description").value("High-speed internet"));

        List<Amenity> all = amenityRepository.findAll();
        assertThat(all).hasSize(1);
        assertThat(all.get(0).getName()).isEqualTo("WiFi");
    }

    @Test
    @DisplayName("GET /api/amenities returns 200 and list after a successful create")
    void getAll_afterCreate_ok() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("Parking", "On-site"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/amenities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Parking"))
                .andExpect(jsonPath("$[0].description").value("On-site"));
    }

    @Test
    @DisplayName("POST /api/amenities allows null description (host) and returns 200")
    void create_nullDescription_ok() throws Exception {
        mockMvc.perform(post("/api/amenities")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_host")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req("Air Conditioning", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Air Conditioning"))
                .andExpect(jsonPath("$.description").doesNotExist()); // ili isEmpty() u zavisnosti od mappera

        var found = amenityRepository.findAll();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getDescription()).isNull();
    }
}
