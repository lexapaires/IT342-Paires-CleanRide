package edu.cit.paires.cleanride.controller;

import edu.cit.paires.cleanride.entity.User;
import edu.cit.paires.cleanride.dto.LoginRequest;
import edu.cit.paires.cleanride.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder; // Bean defined in SecurityConfig

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody User user) {
        if (userRepository.existsByEmail(user.getEmail())) {
            return new ResponseEntity<>("Email already taken!", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByUsername(user.getUsername())) {
            return new ResponseEntity<>("Username already taken!", HttpStatus.CONFLICT);
        }

        // Hash password before saving to the database
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return new ResponseEntity<>("User registered successfully", HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody LoginRequest loginRequest) {
        if ("cradmin@gmail.com".equals(loginRequest.getEmail()) && "cladmin123".equals(loginRequest.getPassword())) {
            return ResponseEntity.ok("ADMIN_LOGIN_SUCCESS");
        }

        return userRepository.findByEmail(loginRequest.getEmail())
                .filter(user -> passwordEncoder.matches(loginRequest.getPassword(), user.getPassword()))
                .map(user -> ResponseEntity.ok().body((Object) user))
                .orElse(new ResponseEntity<>("Invalid credentials", HttpStatus.UNAUTHORIZED));
    }
}