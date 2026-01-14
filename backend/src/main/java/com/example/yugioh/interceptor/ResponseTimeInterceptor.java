package com.example.yugioh.interceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class ResponseTimeInterceptor implements HandlerInterceptor {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeInterceptor.class);
    
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute("startTime", startTime);
        return true;
    }
    
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object startTimeAttr = request.getAttribute("startTime");
        if (startTimeAttr == null) {
            return; // preHandle was not called, skip timing
        }
        
        long startTime = (Long) startTimeAttr;
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String fullUrl = queryString != null ? uri + "?" + queryString : uri;
        
        if (uri.startsWith("/api/")) {
            logger.info("🕒 API Response Time: {} {} - {}ms", method, fullUrl, duration);
            
            response.setHeader("X-Response-Time", duration + "ms");
        }
    }
}
