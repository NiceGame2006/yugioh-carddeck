package com.example.yugioh.service;

import com.example.yugioh.model.User;
import com.example.yugioh.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * User data loader to create initial users in the database.
 * Runs once on application startup if database is empty.
 * 
 * Similar to CardDataLoader but for user initialization.
 */
@Service
public class UserDataLoader implements CommandLineRunner {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    
    public UserDataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }
    
    @Override
    public void run(String... args) throws Exception {
        // Only create users if database is empty
        if (userRepository.count() == 0) {
            // Create regular users
            User user1 = new User();
            user1.setUsername("user1");
            user1.setPassword(passwordEncoder.encode("password1"));
            user1.setRole("ROLE_USER");
            user1.setEnabled(true);
            userRepository.save(user1);
            System.out.println("Created user: user1/password1 with ROLE_USER");
            
            User user2 = new User();
            user2.setUsername("user2");
            user2.setPassword(passwordEncoder.encode("password2"));
            user2.setRole("ROLE_USER");
            user2.setEnabled(true);
            userRepository.save(user2);
            System.out.println("Created user: user2/password2 with ROLE_USER");
            
            // Create admin users
            User admin1 = new User();
            admin1.setUsername("admin1");
            admin1.setPassword(passwordEncoder.encode("password1"));
            admin1.setRole("ROLE_ADMIN");
            admin1.setEnabled(true);
            userRepository.save(admin1);
            System.out.println("Created user: admin1/password1 with ROLE_ADMIN");
            
            User admin2 = new User();
            admin2.setUsername("admin2");
            admin2.setPassword(passwordEncoder.encode("password2"));
            admin2.setRole("ROLE_ADMIN");
            admin2.setEnabled(true);
            userRepository.save(admin2);
            System.out.println("Created user: admin2/password2 with ROLE_ADMIN");
            
            System.out.println("Initial users created successfully!");
        } else {
            System.out.println("Users already exist, skipping initialization.");
        }
    }
}
