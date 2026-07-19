COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: build up down restart ps logs clean smoke

build:            ## build all service jars (skips tests)
	./mvnw -DskipTests package

test:             ## full build: unit + integration tests
	./mvnw verify

up: build         ## build jars + images, start the full stack
	$(COMPOSE) up -d --build

down:             ## stop the stack (keeps mongo data volume)
	$(COMPOSE) down

restart: down up

ps:               ## show stack status + health
	$(COMPOSE) ps

logs:             ## follow logs of all services
	$(COMPOSE) logs -f order-service inventory-service payment-service notification-service

smoke:            ## run the end-to-end saga smoke test against a running stack
	./scripts/smoke-e2e.sh

clean:            ## stop stack and delete data volumes
	$(COMPOSE) down -v
	./mvnw clean
