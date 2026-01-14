package com.example.yugioh.controller;

import com.example.yugioh.model.User;
import com.example.yugioh.repository.UserRepository;
import com.example.yugioh.dto.ResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {
    
    private final UserRepository userRepository;
    
    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    @GetMapping
    public ResponseEntity<ResponseEnvelope<List<UserDTO>>> getAllUsers() {
        List<User> users = userRepository.findAll();
        // Don't expose passwords - return DTO without password
        List<UserDTO> userDTOs = users.stream()
            .map(u -> new UserDTO(u.getId(), u.getUsername(), u.getRole(), u.isEnabled()))
            .collect(Collectors.toList());
        
        ResponseEnvelope<List<UserDTO>> env = ResponseEnvelope.success("Users fetched", userDTOs);
        return ResponseEntity.ok(env);
    }
    
    // Simple DTO to hide password from API response
    public static class UserDTO {
        private Long id;
        private String username;
        private String role;
        private boolean enabled;
        
        public UserDTO(Long id, String username, String role, boolean enabled) {
            this.id = id;
            this.username = username;
            this.role = role;
            this.enabled = enabled;
        }
        
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getRole() { return role; }
        public boolean isEnabled() { return enabled; }
    }
}
