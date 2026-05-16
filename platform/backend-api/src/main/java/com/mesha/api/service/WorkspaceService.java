package com.mesha.api.service;

import com.mesha.api.dto.CreateWorkspaceRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.WorkspaceMemberRepository;
import com.mesha.api.repository.WorkspaceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Workspace create(CreateWorkspaceRequest req, User owner) {
        if (workspaceRepository.existsBySlug(req.slug())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slug already taken");
        }
        Workspace ws = new Workspace();
        ws.setName(req.name());
        ws.setSlug(req.slug());
        ws.setCreatedBy(owner);
        ws = workspaceRepository.save(ws);

        WorkspaceMember membership = new WorkspaceMember();
        membership.setWorkspace(ws);
        membership.setUser(owner);
        membership.setRole(WorkspaceRole.OWNER);
        memberRepository.save(membership);

        return ws;
    }

    public List<Workspace> listForUser(UUID userId) {
        return workspaceRepository.findAllByMemberUserId(userId);
    }

    public Workspace getById(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
    }

    public List<WorkspaceMember> listMembers(UUID workspaceId) {
        return memberRepository.findAllByWorkspaceId(workspaceId);
    }

    @Transactional
    public WorkspaceMember updateMemberRole(UUID workspaceId, UUID memberId, WorkspaceRole newRole, User requestingUser) {
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (member.getRole() == WorkspaceRole.OWNER && newRole != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change owner role");
        }
        member.setRole(newRole);
        return memberRepository.save(member);
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId) {
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (member.getRole() == WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove workspace owner");
        }
        memberRepository.delete(member);
    }

    public boolean hasRole(UUID workspaceId, UUID userId, List<WorkspaceRole> roles) {
        return memberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(workspaceId, userId, roles);
    }
}
