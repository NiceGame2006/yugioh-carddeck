.PHONY: help dev-up dev-down prod-up prod-down logs clean rebuild status

# Colors for output
GREEN=\033[0;32m
YELLOW=\033[1;33m
NC=\033[0m # No Color

help: ## Show this help message
	@echo "$(GREEN)Yu-Gi-Oh App - Docker Compose Commands$(NC)"
	@echo ""
	@echo "$(YELLOW)Development:$(NC)"
	@echo "  make dev-up      - Start app in development mode (hot reload, debug ports)"
	@echo "  make dev-down    - Stop development environment"
	@echo "  make dev-logs    - View development logs"
	@echo ""
	@echo "$(YELLOW)Production:$(NC)"
	@echo "  make prod-up     - Start app in production mode (optimized, no debug)"
	@echo "  make prod-down   - Stop production environment"
	@echo "  make prod-logs   - View production logs"
	@echo ""
	@echo "$(YELLOW)Utilities:$(NC)"
	@echo "  make logs        - View all logs (current environment)"
	@echo "  make status      - Show running containers"
	@echo "  make clean       - Stop and remove all containers + volumes (fresh start)"
	@echo "  make rebuild     - Rebuild images (use after code changes)"
	@echo ""

# Development
dev-up: ## Start development environment
	@echo "$(GREEN)Starting development environment...$(NC)"
	@echo "Features: Hot reload, SQL logging, debug port 5005, all ports exposed"
	docker-compose -f docker-compose.dev.yml up -d
	@echo "$(GREEN)✓ Development started!$(NC)"
	@echo "Frontend: http://localhost:3000"
	@echo "Backend:  http://localhost:8080"
	@echo "Debug:    localhost:5005"
	@echo ""
	@echo "View logs: make dev-logs"

dev-down: ## Stop development environment
	@echo "$(YELLOW)Stopping development environment...$(NC)"
	docker-compose -f docker-compose.dev.yml down
	@echo "$(GREEN)✓ Development stopped$(NC)"

dev-logs: ## View development logs
	docker-compose -f docker-compose.dev.yml logs -f

# Production
prod-up: ## Start production environment
	@echo "$(GREEN)Starting production environment...$(NC)"
	@echo "Features: Optimized builds, minimal logging, no debug ports"
	docker-compose -f docker-compose.prod.yml up -d
	@echo "$(GREEN)✓ Production started!$(NC)"
	@echo "Frontend: http://localhost:3000"
	@echo "Backend:  http://localhost:8080"
	@echo ""
	@echo "View logs: make prod-logs"

prod-down: ## Stop production environment
	@echo "$(YELLOW)Stopping production environment...$(NC)"
	docker-compose -f docker-compose.prod.yml down
	@echo "$(GREEN)✓ Production stopped$(NC)"

prod-logs: ## View production logs
	docker-compose -f docker-compose.prod.yml logs -f

# Utilities
logs: ## View logs (auto-detect environment)
	@if docker-compose ps | grep -q "yugioh"; then \
		docker-compose logs -f; \
	else \
		echo "$(YELLOW)No containers running$(NC)"; \
	fi

status: ## Show container status
	@echo "$(GREEN)Container Status:$(NC)"
	@docker-compose -f docker-compose.dev.yml ps 2>/dev/null || docker-compose -f docker-compose.prod.yml ps

clean: ## Stop and remove everything (fresh start)
	@echo "$(YELLOW)⚠️  WARNING: This will delete all data (database, cache, etc.)$(NC)"
	@read -p "Are you sure? [y/N] " -n 1 -r; \
	echo; \
	if [[ $$REPLY =~ ^[Yy]$$ ]]; then \
		echo "$(YELLOW)Cleaning up...$(NC)"; \
		docker-compose -f docker-compose.dev.yml down -v; \
		docker-compose -f docker-compose.prod.yml down -v; \
		echo "$(GREEN)✓ Cleanup complete$(NC)"; \
	else \
		echo "Cancelled"; \
	fi

rebuild: ## Rebuild all images
	@echo "$(YELLOW)Rebuilding images...$(NC)"
	docker-compose -f docker-compose.dev.yml build --no-cache
	docker-compose -f docker-compose.prod.yml build --no-cache
	@echo "$(GREEN)✓ Rebuild complete$(NC)"
	@echo "Start with: make dev-up or make prod-up"

# Quick shortcuts
up: dev-up ## Alias for dev-up
down: dev-down ## Alias for dev-down
restart: down up ## Restart development environment
