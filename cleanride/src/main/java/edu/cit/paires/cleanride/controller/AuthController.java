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
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import edu.cit.paires.cleanride.dto.GoogleAuthRequest;
import edu.cit.paires.cleanride.dto.SetPasswordRequest;

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

        Optional<User> userOpt = userRepository.findByEmail(loginRequest.getEmail());
        
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (user.getGoogleId() != null && passwordEncoder.matches("OAUTH_USER_PLACEHOLDER_PW", user.getPassword())) {
                return new ResponseEntity<>("This account was created with Google. Please use the 'Sign in with Google' button.", HttpStatus.UNAUTHORIZED);
            }
            if (passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())) {
                user.setNeedsPasswordSetup(false);
                return ResponseEntity.ok().body((Object) user);
            }
        }
        
        return new ResponseEntity<>("Invalid credentials", HttpStatus.UNAUTHORIZED);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleAuthRequest request) {
        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList("550037687488-bl56rpcdltj95pgnk1crsb394dhl1p0p.apps.googleusercontent.com")) 
                .build();

            GoogleIdToken idToken = verifier.verify(request.getCredential());
            if (idToken != null) {
                GoogleIdToken.Payload payload = idToken.getPayload();
                String email = payload.getEmail();
                String name = (String) payload.get("name");
                String firstName = (String) payload.get("given_name");
                String lastName = (String) payload.get("family_name");
                String googleId = payload.getSubject();

                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    if (user.getGoogleId() == null) {
                        user.setGoogleId(googleId);
                        userRepository.save(user);
                    }
                    user.setNeedsPasswordSetup(passwordEncoder.matches("OAUTH_USER_PLACEHOLDER_PW", user.getPassword()));
                    return ResponseEntity.ok().body((Object) user);
                } else {
                    // Do not create a user automatically anymore. Tell the frontend to capture their registration.
                    Map<String, String> googleData = new HashMap<>();
                    googleData.put("action", "REGISTER_REQUIRED");
                    googleData.put("email", email);
                    googleData.put("firstName", firstName != null ? firstName : "User");
                    googleData.put("lastName", lastName != null ? lastName : "");
                    googleData.put("googleId", googleId);

                    return ResponseEntity.status(HttpStatus.ACCEPTED).body(googleData);
                }
            } else {
                return new ResponseEntity<>("Invalid ID token", HttpStatus.UNAUTHORIZED);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return new ResponseEntity<>("Token verification failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @PutMapping("/password")
    public ResponseEntity<?> setPassword(@RequestBody SetPasswordRequest request) {
        Optional<User> userOpt = userRepository.findById(request.getUserId());
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(user);
            return ResponseEntity.ok("Password updated successfully");
        }
        return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
    }
}