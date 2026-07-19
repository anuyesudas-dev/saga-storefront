# Deployment guide

Two supported targets, same images, same smoke test:

| Target | Entry point | Best for |
|---|---|---|
| Docker Compose | `infra/docker-compose.yml` | day-to-day development |
| Kubernetes (kind) | `deploy/k8s/` (Kustomize) | probes, secrets, scheduling — production-shaped |

Prerequisites: JDK 17, Docker. The Kubernetes path additionally needs `kind`
and `kubectl`. All commands run from the repository root via `make`
(run `make` targets individually or read the Makefile — it is the task catalog).

## Configuration model

Nothing environment-specific or secret is baked into images or committed
manifests:

- **Compose**: overrides live in `infra/.env` (gitignored), template in
  `infra/.env.example`. Defaults work out of the box.
- **Kubernetes**: secret material lives in `deploy/k8s/.secrets.env`
  (gitignored), template in `deploy/k8s/.secrets.env.example`. Kustomize's
  `secretGenerator` turns it into the `platform-secrets` Secret at apply time.
  Non-secret wiring (Kafka bootstrap) comes from a generated ConfigMap.
- Spring profiles: `local` (bare JVM against Compose infra), `docker`, `k8s`.
  The `docker`/`k8s` profiles switch to structured JSON logging.

## Docker Compose

```bash
cp infra/.env.example infra/.env   # optional — defaults work
make build     # jars (skips tests) + docker images
make up        # kafka (KRaft), mongo (replica set), 4 services, prometheus, grafana
make ps        # wait until all healthchecks are green
make smoke     # 3-path saga smoke test + log correlation check
make down      # stop; make clean also removes volumes
```

Endpoints: services on 8081–8084 (`/swagger-ui.html`), Prometheus on 9090,
Grafana on 3000. Mongo runs **without auth** here — a documented local-only
trade-off; the Kubernetes deployment turns auth on.

## Kubernetes (kind)

```bash
cp deploy/k8s/.secrets.env.example deploy/k8s/.secrets.env   # edit values
make k8s-cluster    # kind create cluster --name ecommerce
make k8s-images     # build images + kind load
make k8s-apply      # kubectl apply -k deploy/k8s
make k8s-status     # wait until all 8 pods are 1/1 Running
make k8s-forward    # port-forwards 8081-8084, 9090, 3000 (foreground; Ctrl-C stops)
make k8s-smoke      # smoke test through the forwards (SMOKE_LOGS=k8s → kubectl logs)
make k8s-down       # delete the cluster
```

What differs from Compose (design notes in `deploy/k8s/README.md`):

- **MongoDB auth is on** — root credentials from the Secret; replica-set
  internal auth via keyFile prepared by an idempotent initContainer.
- **Kafka pins `CLUSTER_ID`** — the data PVC outlives pods; a regenerated ID
  would conflict with `meta.properties` on restart.
- **Prometheus discovers pods by annotation** (`prometheus.io/scrape|port|path`)
  with least-privilege RBAC, instead of a static target list.
- **Grafana requires login** — credentials from the Secret; provisioning files
  are copies of `infra/grafana` (source of truth) — refresh with
  `make k8s-sync-grafana`.
- Containers run non-root with dropped capabilities; startup probes absorb JVM
  boot so liveness stays tight.

Resource footprint: the full stack fits in ~2.7 GiB; a 4 GiB WSL/VM allocation
is workable, below that pods may fail to schedule.

## OpenShift

Deliberately **not** implemented — OpenShift Local cannot run inside this
project's WSL2 environment and its VM needs ~11 GiB by itself. The decision,
and what an OpenShift port would require (Routes, `restricted-v2` SCC
compliance, registry-based image delivery), is recorded in
[ADR-0007](adr/0007-scope-stop-at-kubernetes.md). The containers already run
non-root with dropped capabilities, which covers most of the SCC distance.
