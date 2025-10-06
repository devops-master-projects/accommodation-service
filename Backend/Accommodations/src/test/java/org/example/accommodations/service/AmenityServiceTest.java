package org.example.accommodations.service;

import org.example.accommodations.dto.AmenityRequestDto;
import org.example.accommodations.dto.AmenityResponseDto;
import org.example.accommodations.model.Amenity;
import org.example.accommodations.repository.AmenityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class AmenityServiceTest {

    @Mock
    private AmenityRepository repository;

    @InjectMocks
    private AmenityService service;

    private AmenityRequestDto makeReq(String name) {
        AmenityRequestDto req = new AmenityRequestDto();
        req.setName(name);
        return req;
    }

    private Amenity makeEntity(UUID id, String name) {
        return Amenity.builder().id(id).name(name).build();
    }

    // create

    @Test
    void create_whenNameNotExists_persists_and_returnsDto() {
        // given
        AmenityRequestDto req = makeReq("WiFi");
        given(repository.existsByName("WiFi")).willReturn(false);

        // simulate DB assigning ID
        UUID newId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        given(repository.save(any(Amenity.class))).willAnswer(inv -> {
            Amenity a = inv.getArgument(0);
            // emulate generated ID if null
            if (a.getId() == null) {
                a = Amenity.builder().id(newId).name(a.getName()).build();
            }
            return a;
        });

        // when
        AmenityResponseDto out = service.create(req);

        // then
        assertThat(out).isNotNull();
        assertThat(out.getId()).isEqualTo(newId);
        assertThat(out.getName()).isEqualTo("WiFi");

        ArgumentCaptor<Amenity> captor = ArgumentCaptor.forClass(Amenity.class);
        verify(repository).existsByName("WiFi");
        verify(repository).save(captor.capture());

        Amenity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("WiFi");
    }

    @Test
    void create_whenNameExists_throws_and_doesNotSave() {
        // given
        AmenityRequestDto req = makeReq("Parking");
        given(repository.existsByName("Parking")).willReturn(true);

        // when / then
        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already exists");

        verify(repository).existsByName("Parking");
        verify(repository, never()).save(any());
    }

    // getAll

    @Test
    void getAll_whenThereAreAmenities_returnsOkAndMappedDtos() {
        // given
        Amenity a1 = makeEntity(UUID.fromString("11111111-1111-1111-1111-111111111111"), "WiFi");
        Amenity a2 = makeEntity(UUID.fromString("22222222-2222-2222-2222-222222222222"), "Parking");
        given(repository.findAll()).willReturn(List.of(a1, a2));

        // when
        ResponseEntity<List<AmenityResponseDto>> resp = service.getAll();

        // then
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody()).extracting(AmenityResponseDto::getName)
                .containsExactly("WiFi", "Parking");
        assertThat(resp.getBody()).extracting(AmenityResponseDto::getId)
                .containsExactly(a1.getId(), a2.getId());

        verify(repository).findAll();
    }

    @Test
    void getAll_whenEmpty_returnsOkWithEmptyList() {
        // given
        given(repository.findAll()).willReturn(List.of());

        // when
        ResponseEntity<List<AmenityResponseDto>> resp = service.getAll();

        // then
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull().isEmpty();

        verify(repository).findAll();
    }
}
