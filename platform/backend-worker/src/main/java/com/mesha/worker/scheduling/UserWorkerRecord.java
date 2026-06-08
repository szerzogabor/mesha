package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class UserWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column
    private String name;

    @Column
    private String email;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }

    public String getDisplayName() {
        return (name != null && !name.isBlank()) ? name : email;
    }
}
