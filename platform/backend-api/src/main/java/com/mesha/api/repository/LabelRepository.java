package com.mesha.api.repository;

import com.mesha.api.model.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface LabelRepository extends JpaRepository<Label, UUID> {
    List<Label> findAllByWorkspaceIdOrderByNameAsc(UUID workspaceId);
    boolean existsByWorkspaceIdAndName(UUID workspaceId, String name);
}
