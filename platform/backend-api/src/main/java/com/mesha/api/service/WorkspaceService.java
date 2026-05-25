package com.mesha.api.service;

import com.mesha.api.dto.CreateWorkspaceRequest;
import com.mesha.api.model.*;
import com.mesha.api.repository.WorkspaceMemberRepository;
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
public class WorkspaceService {

    private static final Logger log = LoggerFactory.getLogger(WorkspaceService.class);

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceService(WorkspaceRepository workspaceRepository,
                            WorkspaceMemberRepository memberRepository) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
    }

    @Transactional
    public Workspace create(CreateWorkspaceRequest req, User owner) {
        log.debug("Creating workspace slug={} ownerId={}", req.slug(), owner.getId());
        if (workspaceRepository.existsBySlug(req.slug())) {
            log.debug("Workspace slug conflict slug={}", req.slug());
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

        log.info("Workspace created workspaceId={} slug={} ownerId={}", ws.getId(), req.slug(), owner.getId());
        return ws;
    }

    public List<Workspace> listForUser(UUID userId) {
        long startMs = System.currentTimeMillis();
        List<Workspace> workspaces = workspaceRepository.findAllByMemberUserId(userId);
        log.info("Listed workspaces userId={} count={} durationMs={}", userId, workspaces.size(), System.currentTimeMillis() - startMs);
        return workspaces;
    }

    public Workspace getById(UUID workspaceId) {
        long startMs = System.currentTimeMillis();
        Workspace ws = workspaceRepository.findById(workspaceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        log.info("Fetched workspace workspaceId={} durationMs={}", workspaceId, System.currentTimeMillis() - startMs);
        return ws;
    }

    public List<WorkspaceMember> listMembers(UUID workspaceId) {
        long startMs = System.currentTimeMillis();
        List<WorkspaceMember> members = memberRepository.findAllByWorkspaceId(workspaceId);
        log.info("Listed workspace members count={} durationMs={}", members.size(), System.currentTimeMillis() - startMs);
        return members;
    }

    @Transactional
    public WorkspaceMember updateMemberRole(UUID workspaceId, UUID memberId, WorkspaceRole newRole, User requestingUser) {
        log.info("Updating member role workspaceId={} memberId={} newRole={} requestingUserId={}",
                workspaceId, memberId, newRole, requestingUser.getId());
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (member.getRole() == WorkspaceRole.OWNER && newRole != WorkspaceRole.OWNER) {
            log.warn("Attempt to change owner role workspaceId={} memberId={} requestingUserId={}",
                    workspaceId, memberId, requestingUser.getId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot change owner role");
        }
        WorkspaceRole oldRole = member.getRole();
        member.setRole(newRole);
        WorkspaceMember saved = memberRepository.save(member);
        log.info("Member role updated workspaceId={} memberId={} oldRole={} newRole={}",
                workspaceId, memberId, oldRole, newRole);
        return saved;
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID memberId) {
        log.info("Removing workspace member workspaceId={} memberId={}", workspaceId, memberId);
        WorkspaceMember member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));

        if (!member.getWorkspace().getId().equals(workspaceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found");
        }
        if (member.getRole() == WorkspaceRole.OWNER) {
            log.warn("Attempt to remove workspace owner workspaceId={} memberId={}", workspaceId, memberId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot remove workspace owner");
        }
        memberRepository.delete(member);
        log.info("Workspace member removed workspaceId={} memberId={}", workspaceId, memberId);
    }

    public boolean hasRole(UUID workspaceId, UUID userId, List<WorkspaceRole> roles) {
        return memberRepository.existsByWorkspaceIdAndUserIdAndRoleIn(workspaceId, userId, roles);
    }
}
