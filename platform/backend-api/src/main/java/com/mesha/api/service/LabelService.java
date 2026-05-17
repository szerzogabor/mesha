package com.mesha.api.service;

import com.mesha.api.dto.CreateLabelRequest;
import com.mesha.api.model.Label;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.LabelRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LabelService {

    private final LabelRepository labelRepository;
    private final WorkspaceRepository workspaceRepository;

    public LabelService(LabelRepository labelRepository, WorkspaceRepository workspaceRepository) {
        this.labelRepository = labelRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public Label create(UUID workspaceId, CreateLabelRequest req) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        if (labelRepository.existsByWorkspaceIdAndName(workspaceId, req.name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Label name already exists in this workspace");
        }

        Label label = new Label();
        label.setWorkspace(workspace);
        label.setName(req.name());
        if (req.color() != null) label.setColor(req.color());
        return labelRepository.save(label);
    }

    public List<Label> listByWorkspace(UUID workspaceId) {
        return labelRepository.findAllByWorkspaceIdOrderByNameAsc(workspaceId);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID labelId) {
        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
        if (!label.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        }
        labelRepository.delete(label);
    }
}
