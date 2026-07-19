# Kubernetes deployment (local kind cluster)

Runs the full platform — Kafka (KRaft), MongoDB (auth + single-member replica
set), the four internal services, auth-service, api-gateway, the React
frontend, Prometheus, Grafana — on a local
[kind](https://kind.sigs.k8s.io/) cluster.

## Prerequisites

- Docker
- `kubectl` and `kind` on the PATH

## Deploy

```bash
# one-time: secret material (gitignored, generated into a Secret at apply time)
cp deploy/k8s/.secrets.env.example deploy/k8s/.secrets.env   # then edit values

make k8s-cluster     # create the kind cluster
make k8s-images      # build jars + images, load into kind
make k8s-apply       # kubectl apply -k deploy/k8s
make k8s-status      # watch until all pods are Ready

make k8s-forward     # port-forward 8081-8085, 8080 (gateway), 8090 (frontend), 9090, 3000
make k8s-smoke       # end-to-end saga smoke test through the forwards
make k8s-down        # delete the cluster
```

## Design notes

- **Secrets** are generated from the gitignored `.secrets.env` (kustomize
  `secretGenerator`) — the platform rule: no credentials in committed files.
- **MongoDB auth is ON** (deferred in compose): root credentials from the
  Secret; replica-set internal auth via keyFile, permission-fixed by an
  initContainer because secret mounts can't satisfy mongod's 400-mode rule.
  The readiness probe self-initiates the replica set, so Ready ⇒ transactions work.
- **Kafka** pins `CLUSTER_ID`: with a PVC, a regenerated id would conflict
  with `meta.properties` after a pod restart.
- **Prometheus** discovers targets via pod annotations
  (`prometheus.io/scrape|port|path`) with least-privilege RBAC, replacing the
  compose static target list. `instance` is relabeled to the `app` pod label,
  so the Grafana dashboard works unchanged.
- **Grafana** has no anonymous access here; admin credentials come from the
  Secret. Provisioning files are copies of `infra/grafana` (source of truth) —
  refresh with `make k8s-sync-grafana`.
- **Services**: startup probes absorb JVM boot; readiness/liveness hit the
  actuator endpoints; containers run non-root with dropped capabilities
  (which also preps the OpenShift restricted SCC in the next phase).
- **frontend** is the one exception to non-root: stock `nginx:alpine`'s
  master process wants root to bind/write its pidfile. `nginxinc/nginx-unprivileged`
  is the documented evolution if it needs to match the other containers.
