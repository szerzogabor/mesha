package com.mesha.api.service;

import com.mesha.api.dto.CreateLabelRequest;
import com.mesha.api.model.Label;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.LabelRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class LabelService {

    private static final Logger log = LoggerFactory.getLogger(LabelService.class);

    private final LabelRepository labelRepository;
    private final WorkspaceRepository workspaceRepository;

    public LabelService(LabelRepository labelRepository, WorkspaceRepository workspaceRepository) {
        this.labelRepository = labelRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public Label create(UUID workspaceId, CreateLabelRequest req) {
        log.debug("Creating label workspaceId={} name={}", workspaceId, req.name());
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        if (labelRepository.existsByWorkspaceIdAndName(workspaceId, req.name())) {
            log.debug("Label name conflict workspaceId={} name={}", workspaceId, req.name());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Label name already exists in this workspace");
        }

        Label label = new Label();
        label.setWorkspace(workspace);
        label.setName(req.name());
        if (req.color() != null) label.setColor(req.color());
        label = labelRepository.save(label);
        log.debug("Label created labelId={} workspaceId={} name={}", label.getId(), workspaceId, req.name());
        return label;
    }

    public List<Label> listByWorkspace(UUID workspaceId) {
        long startMs = System.currentTimeMillis();
        List<Label> labels = labelRepository.findAllByWorkspaceIdOrderByNameAsc(workspaceId);
        log.info("Listed labels count={} durationMs={}", labels.size(), System.currentTimeMillis() - startMs);
        return labels;
    }

    @Transactional
    public void delete(UUID workspaceId, UUID labelId) {
        log.debug("Deleting label labelId={} workspaceId={}", labelId, workspaceId);
        Label label = labelRepository.findById(labelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found"));
        if (!label.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Label not found");
        }
        labelRepository.delete(label);
        log.debug("Label deleted labelId={} workspaceId={}", labelId, workspaceId);
    }
}
