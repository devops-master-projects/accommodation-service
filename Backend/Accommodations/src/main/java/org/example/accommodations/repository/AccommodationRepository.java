package org.example.accommodations.repository;


import jakarta.transaction.Transactional;
import org.example.accommodations.model.Accommodation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccommodationRepository extends JpaRepository<Accommodation, UUID> {
    @Query("SELECT a FROM Accommodation a LEFT JOIN FETCH a.photos")
    List<Accommodation> findAllWithPhotos();

    @Query("SELECT a FROM Accommodation a " +
            "LEFT JOIN FETCH a.photos " +
            "LEFT JOIN FETCH a.amenities " +
            "WHERE a.id = :id")
    Optional<Accommodation> findByIdWithDetails(@Param("id") UUID id);

    @Query("SELECT DISTINCT a FROM Accommodation a " +
            "LEFT JOIN FETCH a.photos " +
            "LEFT JOIN FETCH a.amenities")
    List<Accommodation> findAllWithDetails();

    @Query("SELECT a.id FROM Accommodation a WHERE a.hostId = :hostId")
    List<UUID> findAllIdsByHostId(@Param("hostId") UUID hostId);

    @Transactional
    void deleteAllByIdInBatch(Iterable<UUID> ids);

}