package org.example.accommodations.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.*;

@Entity
@Table(name = "accommodations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Accommodation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private Integer minGuests;

    @Column(nullable = false)
    private Integer maxGuests;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "location_id", nullable = false)
    private Location location;

    @Column(columnDefinition = "TEXT")
    private String description;

    private Boolean autoConfirm;

    private String pricingMode;

    // Relations
    @OneToMany(mappedBy = "accommodation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Photo> photos = new HashSet<>();

    @OneToMany(mappedBy = "accommodation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Availability> availability;

    @ManyToMany
    @JoinTable(
            name = "accommodation_amenities",
            joinColumns = @JoinColumn(name = "accommodation_id"),
            inverseJoinColumns = @JoinColumn(name = "amenity_id")
    )
    private Set<Amenity> amenities = new HashSet<>();


    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
