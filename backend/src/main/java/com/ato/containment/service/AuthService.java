package com.ato.containment.service;

import com.ato.containment.model.User;
import com.ato.containment.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Verifies a login attempt. Does not know about sessions — AuthController
 * decides what to do once it knows whether the credentials were correct.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the matching user if the email exists and the password is
     * correct. Deliberately gives the same result (empty) whether the email
     * does not exist or the password is wrong, so a login failure never
     * reveals which part was incorrect.
     */
    public Optional<User> authenticate(String email, String rawPassword) {
        if (email == null || rawPassword == null) {
            return Optional.empty();
        }
        return userRepository.findByEmailIgnoreCase(email)
                .filter(user -> passwordEncoder.matches(rawPassword, user.getPasswordHash()));
    }
}
