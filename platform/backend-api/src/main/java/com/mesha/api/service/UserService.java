package com.mesha.api.service;

import com.mesha.api.model.User;
import com.mesha.api.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User syncUser(String clerkUserId, String email, String name) {
        log.debug("Syncing user clerkUserId={}", clerkUserId);
        return userRepository.findByClerkUserId(clerkUserId)
            .map(existing -> {
                existing.setEmail(email);
                if (name != null) existing.setName(name);
                User saved = userRepository.save(existing);
                log.debug("User synced (updated) clerkUserId={} userId={}", clerkUserId, saved.getId());
                return saved;
            })
            .orElseGet(() -> {
                User user = new User();
                user.setClerkUserId(clerkUserId);
                user.setEmail(email);
                user.setName(name);
                User saved = userRepository.save(user);
                log.info("User synced (created) clerkUserId={} userId={}", clerkUserId, saved.getId());
                return saved;
            });
    }

    public User getByClerkUserId(String clerkUserId) {
        log.debug("Looking up user clerkUserId={}", clerkUserId);
        return userRepository.findByClerkUserId(clerkUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + clerkUserId));
    }
}
