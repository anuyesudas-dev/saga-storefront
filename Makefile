COMPOSE := docker compose -f infra/docker-compose.yml

.PHONY: build up down restart ps logs clean smoke \
        k8s-cluster k8s-images k8s-apply k8s-status k8s-down

KIND_CLUSTER := ecommerce
SERVICES     := order-service inventory-service payment-service notification-service

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

# ---------------------------------------------------------------- kubernetes

k8s-cluster:      ## create the local kind cluster
	kind create cluster --name $(KIND_CLUSTER) --wait 120s

k8s-images: build ## build service images and load them into kind
	for s in $(SERVICES); do docker build -t ecommerce/$$s:0.1.0 services/$$s; done
	kind load docker-image $(SERVICES:%=ecommerce/%:0.1.0) --name $(KIND_CLUSTER)

k8s-apply:        ## deploy everything (needs deploy/k8s/.secrets.env — see .secrets.env.example)
	kubectl apply -k deploy/k8s

k8s-status:       ## show pods + services in the ecommerce namespace
	kubectl -n ecommerce get pods,svc

k8s-down:         ## delete the kind cluster entirely
	kind delete cluster --name $(KIND_CLUSTER)
