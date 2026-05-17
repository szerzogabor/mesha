package com.mesha.api.service;

import com.mesha.api.dto.CreateProjectRequest;
import com.mesha.api.dto.UpdateProjectRequest;
import com.mesha.api.model.Project;
import com.mesha.api.model.Workspace;
import com.mesha.api.repository.ProjectRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final WorkspaceRepository workspaceRepository;

    public ProjectService(ProjectRepository projectRepository, WorkspaceRepository workspaceRepository) {
        this.projectRepository = projectRepository;
        this.workspaceRepository = workspaceRepository;
    }

    @Transactional
    public Project create(UUID workspaceId, CreateProjectRequest req) {
        Workspace workspace = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));

        Project project = new Project();
        project.setWorkspace(workspace);
        project.setName(req.name());
        project.setDescription(req.description());
        return projectRepository.save(project);
    }

    public List<Project> listByWorkspace(UUID workspaceId) {
        return projectRepository.findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
    }

    public Project getById(UUID projectId) {
        return projectRepository.findById(projectId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
    }

    @Transactional
    public Project update(UUID projectId, UpdateProjectRequest req) {
        Project project = getById(projectId);
        if (req.name() != null && !req.name().isBlank()) {
            project.setName(req.name());
        }
        if (req.description() != null) {
            project.setDescription(req.description());
        }
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(UUID projectId) {
        Project project = getById(projectId);
        projectRepository.delete(project);
    }
}
