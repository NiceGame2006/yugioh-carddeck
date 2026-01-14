package com.example.yugioh.dto;

import org.springframework.data.domain.Page;
import java.io.Serializable;
import java.util.List;

// List wrapper with pagination metadata - used for large datasets like Card (13k+ items)
public class PaginatedResponse<T> implements Serializable {
    private static final long serialVersionUID = 1L;

    private List<T> items;
    private int currentPage;
    private int pageSize;
    private int totalPages;
    private long totalItems;
    private boolean hasNext;
    private boolean hasPrevious;

    public PaginatedResponse() {}

    public PaginatedResponse(List<T> items, int currentPage, int pageSize, 
                           int totalPages, long totalItems, boolean hasNext, boolean hasPrevious) {
        this.items = items;
        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
        this.totalItems = totalItems;
        this.hasNext = hasNext;
        this.hasPrevious = hasPrevious;
    }

    public static <T> PaginatedResponse<T> fromPage(Page<T> page) {
        return new PaginatedResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalPages(),
            page.getTotalElements(),
            page.hasNext(),
            page.hasPrevious()
        );
    }

    public List<T> getItems() { return items; }
    public void setItems(List<T> items) { this.items = items; }

    public int getCurrentPage() { return currentPage; }
    public void setCurrentPage(int currentPage) { this.currentPage = currentPage; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public int getTotalPages() { return totalPages; }
    public void setTotalPages(int totalPages) { this.totalPages = totalPages; }

    public long getTotalItems() { return totalItems; }
    public void setTotalItems(long totalItems) { this.totalItems = totalItems; }

    public boolean isHasNext() { return hasNext; }
    public void setHasNext(boolean hasNext) { this.hasNext = hasNext; }

    public boolean isHasPrevious() { return hasPrevious; }
    public void setHasPrevious(boolean hasPrevious) { this.hasPrevious = hasPrevious; }
}
