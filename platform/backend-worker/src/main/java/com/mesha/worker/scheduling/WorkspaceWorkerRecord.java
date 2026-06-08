package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class WorkspaceWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column
    private String name;

    public UUID getId() { return id; }
    public String getName() { return name; }
}
