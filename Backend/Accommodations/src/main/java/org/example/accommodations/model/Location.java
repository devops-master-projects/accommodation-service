package org.example.accommodations.model;
import lombok.*;
import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "locations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Location {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String city;

    private String address;

    private String postalCode;

    @PrePersist
    public void generateId() {
        if (id == null) {
            id = UUID.randomUUID();
        }
    }
}
