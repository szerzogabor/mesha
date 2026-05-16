package com.mesha.api.service;

import com.mesha.api.model.User;
import com.mesha.api.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User syncUser(String clerkUserId, String email, String name) {
        return userRepository.findByClerkUserId(clerkUserId)
            .map(existing -> {
                existing.setEmail(email);
                if (name != null) existing.setName(name);
                return userRepository.save(existing);
            })
            .orElseGet(() -> {
                User user = new User();
                user.setClerkUserId(clerkUserId);
                user.setEmail(email);
                user.setName(name);
                return userRepository.save(user);
            });
    }

    public User getByClerkUserId(String clerkUserId) {
        return userRepository.findByClerkUserId(clerkUserId)
            .orElseThrow(() -> new IllegalStateException("User not found: " + clerkUserId));
    }
}
