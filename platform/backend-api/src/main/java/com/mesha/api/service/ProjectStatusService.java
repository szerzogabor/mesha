package com.mesha.api.service;

import com.mesha.api.dto.CreateProjectStatusRequest;
import com.mesha.api.dto.UpdateProjectStatusRequest;
import com.mesha.api.model.Project;
import com.mesha.api.model.ProjectStatus;
import com.mesha.api.repository.ProjectRepository;
import com.mesha.api.repository.ProjectStatusRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectStatusService {

    private static final Logger log = LoggerFactory.getLogger(ProjectStatusService.class);

    static final List<String[]> DEFAULT_STATUSES = List.of(
        new String[]{"BACKLOG",     "#94a3b8"},
        new String[]{"TODO",        "#3b82f6"},
        new String[]{"IN_PROGRESS", "#f59e0b"},
        new String[]{"REVIEW",      "#8b5cf6"},
        new String[]{"DONE",        "#22c55e"}
    );

    private final ProjectStatusRepository statusRepository;
    private final ProjectRepository projectRepository;

    public ProjectStatusService(ProjectStatusRepository statusRepository,
                                ProjectRepository projectRepository) {
        this.statusRepository = statusRepository;
        this.projectRepository = projectRepository;
    }

    public List<ProjectStatus> list(UUID projectId) {
        return statusRepository.findAllByProjectIdOrderByPositionAsc(projectId);
    }

    @Transactional
    public void seedDefaultStatuses(Project project) {
        for (int i = 0; i < DEFAULT_STATUSES.size(); i++) {
            String[] def = DEFAULT_STATUSES.get(i);
            ProjectStatus s = new ProjectStatus();
            s.setProject(project);
            s.setName(def[0]);
            s.setColor(def[1]);
            s.setPosition(i);
            statusRepository.save(s);
        }
        log.info("Seeded default statuses for projectId={}", project.getId());
    }

    @Transactional
    public ProjectStatus create(UUID projectId, CreateProjectStatusRequest req) {
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        String name = req.name().trim().toUpperCase().replace(" ", "_");
        if (statusRepository.existsByProjectIdAndName(projectId, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Status with this name already exists");
        }

        ProjectStatus status = new ProjectStatus();
        status.setProject(project);
        status.setName(name);
        status.setColor(req.color() != null ? req.color() : "#6366f1");
        status.setPosition(statusRepository.nextPositionForProject(projectId));
        ProjectStatus saved = statusRepository.save(status);
        log.info("Created status statusId={} projectId={} name={}", saved.getId(), projectId, name);
        return saved;
    }

    @Transactional
    public ProjectStatus update(UUID projectId, UUID statusId, UpdateProjectStatusRequest req) {
        ProjectStatus status = getById(projectId, statusId);

        if (req.name() != null && !req.name().isBlank()) {
            String newName = req.name().trim().toUpperCase().replace(" ", "_");
            if (!newName.equals(status.getName()) && statusRepository.existsByProjectIdAndName(projectId, newName)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Status with this name already exists");
            }
            status.setName(newName);
        }
        if (req.color() != null) {
            status.setColor(req.color());
        }

        ProjectStatus saved = statusRepository.save(status);
        log.info("Updated status statusId={} projectId={}", statusId, projectId);
        return saved;
    }

    @Transactional
    public void delete(UUID projectId, UUID statusId) {
        ProjectStatus status = getById(projectId, statusId);
        statusRepository.delete(status);
        log.info("Deleted status statusId={} projectId={}", statusId, projectId);
    }

    @Transactional
    public List<ProjectStatus> reorder(UUID projectId, List<UUID> statusIds) {
        List<ProjectStatus> statuses = statusRepository.findAllByProjectIdOrderByPositionAsc(projectId);
        if (statuses.size() != statusIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status IDs list must contain all statuses for this project");
        }

        for (int i = 0; i < statusIds.size(); i++) {
            final int position = i;
            UUID id = statusIds.get(i);
            statuses.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status ID: " + id))
                .setPosition(position);
        }

        statusRepository.saveAll(statuses);
        log.info("Reordered statuses for projectId={}", projectId);
        return statusRepository.findAllByProjectIdOrderByPositionAsc(projectId);
    }

    private ProjectStatus getById(UUID projectId, UUID statusId) {
        return statusRepository.findById(statusId)
            .filter(s -> s.getProject().getId().equals(projectId))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Status not found"));
    }
}
