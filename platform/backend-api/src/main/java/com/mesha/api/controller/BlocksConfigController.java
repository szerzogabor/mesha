package com.mesha.api.controller;

import com.mesha.api.dto.BlocksConfigDto;
import com.mesha.api.dto.SaveBlocksConfigRequest;
import com.mesha.api.service.BlocksConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/blocks/config")
public class BlocksConfigController {

    private static final Logger log = LoggerFactory.getLogger(BlocksConfigController.class);

    private final BlocksConfigService blocksConfigService;

    public BlocksConfigController(BlocksConfigService blocksConfigService) {
        this.blocksConfigService = blocksConfigService;
    }

    @GetMapping
    @PreAuthorize("@workspaceSecurity.isMember(authentication, #workspaceId.toString())")
    public ResponseEntity<BlocksConfigDto> getConfig(@PathVariable UUID workspaceId) {
        return blocksConfigService.getConfig(workspaceId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId.toString())")
    public ResponseEntity<BlocksConfigDto> saveConfig(
            @PathVariable UUID workspaceId,
            @RequestBody SaveBlocksConfigRequest request) {
        log.info("Saving Blocks config workspaceId={}", workspaceId);
        BlocksConfigDto dto = blocksConfigService.saveConfig(workspaceId, request.apiKey(), request.blocksWorkspaceId());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping
    @PreAuthorize("@workspaceSecurity.isAdminOrAbove(authentication, #workspaceId.toString())")
    public ResponseEntity<Void> deleteConfig(@PathVariable UUID workspaceId) {
        log.info("Deleting Blocks config workspaceId={}", workspaceId);
        blocksConfigService.deleteConfig(workspaceId);
        return ResponseEntity.noContent().build();
    }
}
