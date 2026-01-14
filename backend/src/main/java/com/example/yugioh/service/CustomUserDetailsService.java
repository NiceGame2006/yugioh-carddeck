package com.example.yugioh.service;

import com.example.yugioh.model.User;
import com.example.yugioh.repository.UserRepository;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;

/**
 * Custom UserDetailsService that loads users from the database.
 * 
 * Authentication Flow:
 * 1. User attempts login with username/password
 * 2. Spring Security calls loadUserByUsername(username)
 * 3. This service queries database for user
 * 4. Converts User entity to Spring Security's UserDetails
 * 5. Returns UserDetails with roles converted to GrantedAuthority objects
 * 6. Spring Security verifies password and grants access if valid
 * 
 * GrantedAuthority vs SimpleGrantedAuthority:
 * - GrantedAuthority: Interface representing a permission/role
 * - SimpleGrantedAuthority: Concrete implementation that wraps a role string
 * - This file imports BOTH because:
 *   a) GrantedAuthority is used as return type (Collection<? extends GrantedAuthority>)
 *   b) SimpleGrantedAuthority is the actual object we create
 * - Unlike DeckController which only uses GrantedAuthority implicitly through .getAuthorities(),
 *   this file explicitly creates new SimpleGrantedAuthority objects
 * 
 * Example:
 * - Database stores: role = "ROLE_ADMIN"
 * - This service creates: new SimpleGrantedAuthority("ROLE_ADMIN")
 * - Spring Security receives: Collection<GrantedAuthority> containing that authority
 * - Controllers can then check: authentication.getAuthorities() contains "ROLE_ADMIN"
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
    
    /**
     * Loads user details from database for Spring Security authentication.
     * Called automatically by Spring Security when user attempts to login.
     * 
     * @param username The username to look up
     * @return UserDetails object containing username, password, and authorities (roles)
     * @throws UsernameNotFoundException if user doesn't exist
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
        
        // Convert our User entity to Spring Security's UserDetails
        return new org.springframework.security.core.userdetails.User(
            user.getUsername(),
            user.getPassword(),        // BCrypt hash from database
            user.isEnabled(),          // Is account enabled?
            true,                      // accountNonExpired
            true,                      // credentialsNonExpired
            true,                      // accountNonLocked
            getAuthorities(user)       // Convert role string to GrantedAuthority
        );
    }
    
    /**
     * Converts user's role string to GrantedAuthority collection.
     * 
     * SimpleGrantedAuthority is the concrete class that implements GrantedAuthority.
     * It simply wraps a string (like "ROLE_ADMIN") and returns it when getAuthority() is called.
     * 
     * @param user The user entity from database
     * @return Collection containing one GrantedAuthority with user's role
     */
    private Collection<? extends GrantedAuthority> getAuthorities(User user) {
        // user.getRole() = "ROLE_USER" or "ROLE_ADMIN"
        // SimpleGrantedAuthority wraps it as a GrantedAuthority object
        return Collections.singletonList(new SimpleGrantedAuthority(user.getRole()));
    }
}
