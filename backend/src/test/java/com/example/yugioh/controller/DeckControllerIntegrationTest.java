package com.example.yugioh.controller;

import com.example.yugioh.dto.ResponseEnvelope;
import com.example.yugioh.model.Deck;
import com.example.yugioh.repository.DeckRepository;
import com.example.yugioh.repository.UserRepository;
import com.example.yugioh.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for DeckController.
 * Tests the full flow: HTTP request → Spring Security → Controller → Service → Database.
 * 
 * Uses @SpringBootTest to start real Spring application context with all beans.
 * Uses TestRestTemplate to make real HTTP requests to the application.
 * 
 * These tests are slower than unit tests but verify the entire stack works together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test") // Use application-test.properties if exists
class DeckControllerIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate; // Makes HTTP requests to the app
    
    @Autowired
    private DeckRepository deckRepository; // To verify database changes
    
    @Autowired
    private UserRepository userRepository; // To create test users
    
    @Autowired
    private PasswordEncoder passwordEncoder; // To hash passwords
    
    private String jwtToken; // Stores JWT token for authenticated requests
    
    /**
     * Runs before each test - creates a test user and logs in to get JWT token.
     */
    @BeforeEach
    void setUp() {
        // Clean up database before each test
        deckRepository.deleteAll();
        userRepository.deleteAll();
        
        // Create test user
        User testUser = new User();
        testUser.setUsername("testuser");
        testUser.setPassword(passwordEncoder.encode("password123"));
        testUser.setRole("ROLE_USER");
        userRepository.save(testUser);
        
        // Login to get JWT token
        String loginJson = """
            {
                "username": "testuser",
                "password": "password123"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> request = new HttpEntity<>(loginJson, headers);
        
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/auth/login", 
            request, 
            String.class
        );
        
        // Extract token from response (assumes response contains accessToken field)
        // For simplicity, we'll make a basic assumption about the format
        String responseBody = response.getBody();
        if (responseBody != null && responseBody.contains("accessToken")) {
            // Extract token (this is simplified - real parsing would be better)
            int start = responseBody.indexOf("accessToken") + 15; // Skip "accessToken":"
            int end = responseBody.indexOf("\"", start);
            jwtToken = responseBody.substring(start, end);
        }
    }
    
    @Test
    @DisplayName("Create deck with authentication should save to database")
    @SuppressWarnings("unchecked")
    void testCreateDeck_WithAuthentication_ShouldSaveToDB() {
        // Arrange: Prepare request body
        String deckJson = """
            {
                "name": "My Awesome Deck",
                "description": "Test deck for integration test"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken); // Add JWT token to Authorization header
        
        HttpEntity<String> request = new HttpEntity<>(deckJson, headers);
        
        // Act: Make POST request to create deck
        ResponseEntity<ResponseEnvelope<?>> response = restTemplate.exchange(
            "/api/decks",
            HttpMethod.POST,
            request,
            (Class<ResponseEnvelope<?>>) (Class<?>) ResponseEnvelope.class
        );
        
        // Assert: Response should be 201 Created
        assertEquals(HttpStatus.CREATED, response.getStatusCode(),
            "Should return 201 when deck created successfully");
        
        // Assert: Response body should indicate success
        assertNotNull(response.getBody(), "Response body should not be null");
        assertTrue(response.getBody().isSuccess(), 
            "Response should indicate success");
        
        // Assert: Deck should exist in database
        List<Deck> decks = deckRepository.findByUsername("testuser");
        assertFalse(decks.isEmpty(), "Deck should be saved in database");
        Deck savedDeck = decks.get(0);
        assertNotNull(savedDeck, "Deck should be saved in database");
        assertEquals("My Awesome Deck", savedDeck.getName(), 
            "Deck name should match");
        assertEquals("testuser", savedDeck.getUsername(), 
            "Deck should belong to logged-in user");
    }
    
    @Test
    @DisplayName("Create deck without authentication should return 401")
    @SuppressWarnings("unchecked")
    void testCreateDeck_WithoutAuthentication_ShouldReturn401() {
        // Arrange: Request without Authorization header
        String deckJson = """
            {
                "name": "My Deck",
                "description": "Test"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // Note: No jwtToken added
        
        HttpEntity<String> request = new HttpEntity<>(deckJson, headers);
        
        // Act: Try to create deck without authentication
        ResponseEntity<ResponseEnvelope<?>> response = restTemplate.exchange(
            "/api/decks",
            HttpMethod.POST,
            request,
            (Class<ResponseEnvelope<?>>) (Class<?>) ResponseEnvelope.class
        );
        
        // Assert: Should return 401 Unauthorized
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode(),
            "Should return 401 when not authenticated");
        
        // Assert: Deck should NOT exist in database
        assertTrue(deckRepository.findByUsername("testuser").isEmpty(),
            "Deck should not be created without authentication");
    }
    
    @Test
    @DisplayName("Create deck with invalid data should return 400")
    @SuppressWarnings("unchecked")
    void testCreateDeck_WithInvalidData_ShouldReturn400() {
        // Arrange: Empty deck name (violates @NotBlank validation)
        String invalidDeckJson = """
            {
                "name": "",
                "description": "Test"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        
        HttpEntity<String> request = new HttpEntity<>(invalidDeckJson, headers);
        
        // Act: Try to create deck with invalid data
        ResponseEntity<ResponseEnvelope<?>> response = restTemplate.exchange(
            "/api/decks",
            HttpMethod.POST,
            request,
            (Class<ResponseEnvelope<?>>) (Class<?>) ResponseEnvelope.class
        );
        
        // Assert: Should return 400 Bad Request (validation failed)
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
            "Should return 400 when validation fails");
    }
    
    @Test
    @DisplayName("Get deck list should return all user's decks")
    void testGetDecks_ShouldReturnUserDecks() {
        // Arrange: Create a deck in database
        Deck deck = new Deck();
        deck.setName("Test Deck");
        deck.setUsername("testuser");
        deckRepository.save(deck);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwtToken);
        
        HttpEntity<String> request = new HttpEntity<>(headers);
        
        // Act: Get all decks
        ResponseEntity<String> response = restTemplate.exchange(
            "/api/decks",
            HttpMethod.GET,
            request,
            String.class
        );
        
        // Assert: Should return 200 OK
        assertEquals(HttpStatus.OK, response.getStatusCode(),
            "Should return 200 for GET request");
        
        // Assert: Response should contain the deck name
        String responseBody = response.getBody();
        assertNotNull(responseBody, "Response body should not be null");
        assertTrue(responseBody.contains("Test Deck"),
            "Response should contain the created deck");
    }
    
    @Test
    @DisplayName("Create second deck should fail with 409 Conflict")
    @SuppressWarnings("unchecked")
    void testCreateDeck_SecondDeck_ShouldReturn409() {
        // Arrange: Create first deck
        Deck firstDeck = new Deck();
        firstDeck.setName("First Deck");
        firstDeck.setUsername("testuser");
        deckRepository.save(firstDeck);
        
        // Prepare request for second deck
        String deckJson = """
            {
                "name": "Second Deck",
                "description": "Should fail"
            }
            """;
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwtToken);
        
        HttpEntity<String> request = new HttpEntity<>(deckJson, headers);
        
        // Act: Try to create second deck
        ResponseEntity<ResponseEnvelope<?>> response = restTemplate.exchange(
            "/api/decks",
            HttpMethod.POST,
            request,
            (Class<ResponseEnvelope<?>>) (Class<?>) ResponseEnvelope.class
        );
        
        // Assert: Should return 409 Conflict (user already has a deck)
        assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
            "Should return 409 when user already has a deck");
        
        // Assert: Only one deck should exist
        assertEquals(1, deckRepository.findAll().size(),
            "Should still have only 1 deck in database");
    }
}
