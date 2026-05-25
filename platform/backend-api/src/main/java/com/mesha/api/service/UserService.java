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
        long startMs = System.currentTimeMillis();
        return userRepository.findByClerkUserId(clerkUserId)
            .map(existing -> {
                existing.setEmail(email);
                if (name != null) existing.setName(name);
                User saved = userRepository.save(existing);
                log.info("User synced action=updated clerkUserId={} userId={} durationMs={}", clerkUserId, saved.getId(), System.currentTimeMillis() - startMs);
                return saved;
            })
            .orElseGet(() -> {
                User user = new User();
                user.setClerkUserId(clerkUserId);
                user.setEmail(email);
                user.setName(name);
                User saved = userRepository.save(user);
                log.info("User synced action=created clerkUserId={} userId={} durationMs={}", clerkUserId, saved.getId(), System.currentTimeMillis() - startMs);
                return saved;
            });
    }

    public User getByClerkUserId(String clerkUserId) {
        long startMs = System.currentTimeMillis();
        User user = userRepository.findByClerkUserId(clerkUserId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found: " + clerkUserId));
        log.info("Fetched user clerkUserId={} userId={} durationMs={}", clerkUserId, user.getId(), System.currentTimeMillis() - startMs);
        return user;
    }
}
