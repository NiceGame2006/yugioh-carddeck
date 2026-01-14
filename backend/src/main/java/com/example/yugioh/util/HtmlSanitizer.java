package com.example.yugioh.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * Utility for sanitizing HTML input to prevent XSS (Cross-Site Scripting) attacks.
 * 
 * XSS Attack Example:
 * User submits: <script>alert('hacked')</script>
 * Without sanitization: Script executes in other users' browsers
 * With sanitization: Converted to: &lt;script&gt;alert('hacked')&lt;/script&gt; (plain text)
 * 
 * This utility uses jsoup library with Safelist.none() which:
 * - Removes ALL HTML tags
 * - Escapes special characters (< > & " ')
 * - Allows only plain text
 * 
 * Usage:
 * String safe = HtmlSanitizer.sanitize(userInput);
 */
public class HtmlSanitizer {
    
    /**
     * Sanitizes user input by removing all HTML tags and escaping special characters.
     * 
     * Examples:
     * - Input: "<script>alert('xss')</script>" → Output: ""
     * - Input: "<b>Bold</b> text" → Output: "Bold text"
     * - Input: "Hello <img src=x onerror='alert(1)'>" → Output: "Hello "
     * - Input: "Normal text" → Output: "Normal text"
     * 
     * @param input The raw user input that may contain malicious HTML/JS
     * @return Sanitized string with HTML removed, or null if input was null
     */
    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        
        // Safelist.none() removes ALL HTML, allowing only text content
        // Alternative: Safelist.basic() if you want to allow <b>, <i>, <p> tags
        return Jsoup.clean(input, Safelist.none());
    }
}
