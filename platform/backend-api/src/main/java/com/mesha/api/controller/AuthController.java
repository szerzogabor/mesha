package com.mesha.api.controller;

import com.mesha.api.dto.SyncUserRequest;
import com.mesha.api.dto.UserDto;
import com.mesha.api.model.User;
import com.mesha.api.security.CurrentUser;
import com.mesha.api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    /**
     * Called by the frontend after sign-in to upsert the user record in our database.
     * The Clerk user ID comes from the validated JWT subject claim.
     */
    @PostMapping("/sync")
    public ResponseEntity<UserDto> syncUser(Authentication authentication,
                                            @Valid @RequestBody SyncUserRequest req) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String clerkUserId = jwt.getSubject();
        User user = userService.syncUser(clerkUserId, req.email(), req.name());
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Returns the currently authenticated user's profile.
     */
    @GetMapping("/me")
    public ResponseEntity<UserDto> me(@CurrentUser User user) {
        return ResponseEntity.ok(UserDto.from(user));
    }
}
