package com.example.yugioh.dto;

/**
 * Standard API response wrapper - provides consistent {success, message, data} structure for all endpoints.
 * 
 * Usage (static factory methods only):
 * - Success with data: ResponseEnvelope.success("User created", user)
 * - Success without data: ResponseEnvelope.success("Deleted successfully")
 * - Failure: ResponseEnvelope.failed("Invalid credentials")
 * 
 * Constructors are private to enforce use of static factory methods for better readability and type safety.
 */
public class ResponseEnvelope<T> {
    private boolean success;
    private String message;
    private T data;

    // Required for Jackson JSON deserialization
    public ResponseEnvelope() {}

    // Private - forces use of static factory methods
    private ResponseEnvelope(boolean success, String message, T data) {
        this.success = success;
        this.message = message;
        this.data = data;
    }

    /**
     * Static factory for successful response with data.
     * @param message Success message
     * @param data Response data
     * @return ResponseEnvelope with success=true
     */
    public static <T> ResponseEnvelope<T> success(String message, T data) {
        return new ResponseEnvelope<>(true, message, data);
    }

    /**
     * Static factory for successful response without data.
     * @param message Success message
     * @return ResponseEnvelope with success=true, data=null
     */
    public static <T> ResponseEnvelope<T> success(String message) {
        return new ResponseEnvelope<>(true, message, null);
    }

    /**
     * Static factory for error response.
     * @param message Error message
     * @return ResponseEnvelope with success=false, data=null
     */
    public static <T> ResponseEnvelope<T> failed(String message) {
        return new ResponseEnvelope<>(false, message, null);
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }
}
