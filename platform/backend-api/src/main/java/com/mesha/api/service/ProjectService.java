package com.mesha.api.service;

import com.mesha.api.dto.CreateProjectRequest;
import com.mesha.api.dto.UpdateProjectRequest;
import com.mesha.api.model.Project;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.ProjectRepository;
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
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(ProjectRepository projectRepository, WorkspaceRepository workspaceRepository) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public Project create(UUID workspaceId, CreateProjectRequest req) {
        log.debug("Creating project workspaceId={} name={}", workspaceId, req.name());
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        Project project = new Project();
        project.setWorkspace(workspace);
        project.setName(req.name());
        project.setDescription(req.description());
        project = projectRepository.save(project);
        log.info("Project created projectId={} workspaceId={} name={}", project.getId(), workspaceId, req.name());
        return project;
    }

    public List<Project> listByWorkspace(UUID workspaceId) {
        log.debug("Listing projects workspaceId={}", workspaceId);
        List<Project> projects = projectRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
        log.debug("Listed projects workspaceId={} count={}", workspaceId, projects.size());
        return projects;
    }

    public Project getById(UUID projectId) {
        log.debug("Fetching project projectId={}", projectId);
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public Project update(UUID projectId, UpdateProjectRequest req) {
        log.debug("Updating project projectId={}", projectId);
        Project project = getById(projectId);
        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name());
        }
        if (req.description() != null) {
            project.setDescription(req.description());
        }
        Project saved = projectRepository.save(project);
        log.debug("Project updated projectId={}", projectId);
        return saved;
    }

    @Transactional
    public void delete(UUID projectId) {
        log.info("Deleting project projectId={}", projectId);
        Project project = getById(projectId);
        projectRepository.delete(project);
        log.info("Project deleted projectId={}", projectId);
    }
}
