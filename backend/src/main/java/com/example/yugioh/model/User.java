package com.example.yugioh.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * User entity for authentication and authorization.
 * Stores user credentials and roles in the database.
 * 
 * Database Table:
 * - app_user (name avoids PostgreSQL reserved keyword "user")
 * - Primary key: id (auto-generated)
 * - Unique constraint on username
 * 
 * Security:
 * - Passwords stored as BCrypt hashes
 * - Roles: ROLE_USER, ROLE_ADMIN
 */
@Entity
@Table(name = "app_user")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    @Column(unique = true, nullable = false, length = 50)
    private String username;
    
    @NotBlank(message = "Password is required")
    @Column(nullable = false)
    private String password; // Stored as BCrypt hash
    
    @NotBlank(message = "Role is required")
    @Column(nullable = false, length = 20)
    private String role; // ROLE_USER, ROLE_ADMIN
    
    @Column(nullable = false)
    private boolean enabled = true;

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    
    // Helper method for refresh token service - returns roles as list
    public java.util.List<String> getRoles() {
        return java.util.Collections.singletonList(role);
    }
}
