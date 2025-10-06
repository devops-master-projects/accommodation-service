package org.example.accommodations.controller;
import org.springframework.security.access.prepost.PreAuthorize;

import org.example.accommodations.dto.AccommodationRequestDto;
import org.example.accommodations.dto.AccommodationResponseDto;
import org.example.accommodations.dto.AutoConfirm;
import org.example.accommodations.model.Accommodation;
import org.example.accommodations.service.AccommodationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accommodations")
public class AccommodationController {

    private final AccommodationService accommodationService;

    public AccommodationController(AccommodationService accommodationService) {
        this.accommodationService = accommodationService;
    }

    @GetMapping
    public List<AccommodationResponseDto> getAll()  {
        return accommodationService.getAll();
    }
    @PreAuthorize("permitAll()")
    @GetMapping("/host/{hostId}")
    public List<UUID> getAllByHost(@PathVariable UUID hostId) {
        return accommodationService.getAllIdsByHost(hostId);
    }


    @PreAuthorize("hasRole('host')")
    @PostMapping
    public AccommodationResponseDto create(@RequestBody AccommodationRequestDto request, @AuthenticationPrincipal Jwt jwt) {
        UUID hostId = UUID.fromString(jwt.getClaim("sub"));
        return accommodationService.create(request, hostId);
    }

    @GetMapping("/{accommodationId}/host")
    @PreAuthorize("permitAll()")
    public ResponseEntity<HostInfoResponse> getHostInfo(@PathVariable UUID accommodationId) {
        AccommodationResponseDto acc = accommodationService.getById(accommodationId);

        HostInfoResponse dto = new HostInfoResponse(
                acc.getHostId(),
                acc.getName()
        );

        return ResponseEntity.ok(dto);
    }

    public record HostInfoResponse(UUID hostId, String accommodationName) {}


    @PreAuthorize("permitAll()")
    @GetMapping("/{id}")
    public ResponseEntity<AccommodationResponseDto> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(accommodationService.getById(id));
    }
    @PreAuthorize("hasRole('host')")
    @PutMapping("/{id}")
    public ResponseEntity<AccommodationResponseDto> update(
            @PathVariable UUID id,
            @RequestBody AccommodationRequestDto request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        UUID hostId = UUID.fromString(jwt.getClaim("sub"));
        return ResponseEntity.ok(accommodationService.update(id, request, hostId));
    }
    @PreAuthorize("hasRole('host')")
    @PatchMapping("/{id}/auto-confirm")
    public ResponseEntity<AccommodationResponseDto> updateAutoConfirm(
            @PathVariable UUID id,
            @RequestBody AutoConfirm request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID hostId = UUID.fromString(jwt.getClaim("sub"));

        AccommodationResponseDto updated = accommodationService.updateAutoConfirm(id, request.isAutoConfirm(), hostId);
        return ResponseEntity.ok(updated);
    }

    @PreAuthorize("hasAnyRole('host','guest')")
    @GetMapping("/{id}/auto-confirm")
    public ResponseEntity<AutoConfirm> getAutoConfirm(@PathVariable UUID id) {
        boolean autoConfirm = accommodationService.getAutoConfirm(id);
        return ResponseEntity.ok(new AutoConfirm(autoConfirm));
    }

    @PreAuthorize("hasRole('host')")
    @DeleteMapping
    public ResponseEntity<Void> deleteAccommodations(@RequestBody List<UUID> ids) {
        accommodationService.deleteAccommodationsByIds(ids);
        return ResponseEntity.noContent().build();
    }

}