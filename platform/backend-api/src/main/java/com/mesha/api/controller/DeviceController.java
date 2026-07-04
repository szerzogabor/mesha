package com.mesha.api.controller;

import com.mesha.api.dto.RegisterDeviceRequest;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * Registration endpoints for push-notification device tokens. The authenticated user is
 * resolved from the Clerk JWT; no path scoping is needed since a device belongs to the
 * caller.
 */
@RestController
@RequestMapping("/api/devices")
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    /** Register or refresh the calling user's device token. */
    @PostMapping
    public ResponseEntity<Void> register(@CurrentUser User user,
                                         @Valid @RequestBody RegisterDeviceRequest req) {
        deviceService.register(user, req.fcmToken(), req.platform());
        return ResponseEntity.noContent().build();
    }

    /** Unregister a device token (e.g. when the user turns notifications off). */
    @DeleteMapping
    public ResponseEntity<Void> unregister(@CurrentUser User user,
                                           @RequestParam @NotBlank String fcmToken) {
        deviceService.unregister(user, fcmToken);
        return ResponseEntity.noContent().build();
    }
}
