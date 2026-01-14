package com.example.yugioh.config;

import com.example.yugioh.interceptor.RateLimitInterceptor;
import com.example.yugioh.interceptor.ResponseTimeInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application configuration for web components, async processing, and scheduling.
 * 
 * Key Features Enabled:
 * 
 * @EnableAsync - Enables @Async annotation for non-blocking method execution
 * - Used by: CardEventListener, BackgroundJobService, CardBatchJobService
 * - Thread pool: Default Spring Boot async executor (min 8 threads)
 * - Benefits: Event listeners don't block API responses, batch jobs run in background
 * 
 * @EnableScheduling - Enables @Scheduled annotation for periodic task execution
 * - Used by: BackgroundJobService.processQueuesScheduled() runs every 5 seconds
 * - Used by: CardBatchJobService.cleanupOldCachesScheduled() runs daily at 2 AM
 * - Benefits: Automated maintenance tasks, scheduled cache warmup, periodic statistics
 * 
 * Interceptor Order:
 * 1. RateLimitInterceptor (order=1) - Blocks excessive requests BEFORE processing
 * 2. ResponseTimeInterceptor (order=2) - Logs performance metrics for allowed requests
 * 
 * Why This Order?
 * - Rate limiting is security concern (reject attacks early)
 * - Response time logging only measures legitimate requests (cleaner metrics)
 * - Both run before controller (via HandlerInterceptor.preHandle)
 * 
 * RestTemplate Bean:
 * - Used for external API calls (e.g., fetching card data from third-party APIs)
 * - Simple HTTP client for synchronous requests
 * - Alternative: WebClient for reactive/async HTTP calls
 */
@Configuration
@EnableAsync(proxyTargetClass = true)
@EnableScheduling
public class AppConfig implements WebMvcConfigurer {
    
    private final ResponseTimeInterceptor responseTimeInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;

    public AppConfig(ResponseTimeInterceptor responseTimeInterceptor, RateLimitInterceptor rateLimitInterceptor) {
        this.responseTimeInterceptor = responseTimeInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }
    
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limiting runs first (security concern)
        registry.addInterceptor(rateLimitInterceptor).order(1);
        // Response time logging runs after rate limit check
        registry.addInterceptor(responseTimeInterceptor).order(2);
    }
}
