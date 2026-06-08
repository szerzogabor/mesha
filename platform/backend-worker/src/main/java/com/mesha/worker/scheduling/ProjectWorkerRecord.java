package com.mesha.worker.scheduling;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "projects")
public class ProjectWorkerRecord {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private WorkspaceWorkerRecord workspace;

    public UUID getId() { return id; }
    public String getName() { return name; }
    public WorkspaceWorkerRecord getWorkspace() { return workspace; }
}
