# Runbook

Operating, debugging, and recovering the platform locally. Commands assume the
repository root. `<svc>` is one of `order-service`, `inventory-service`,
`payment-service`, `notification-service`.

## Health checks

| Question | Compose | Kubernetes |
|---|---|---|
| Is everything up? | `make ps` (healthchecks) | `make k8s-status` (all 8 pods `1/1`) |
| Is a service healthy? | `curl :808x/actuator/health` | readiness probe already gates traffic |
| Does the whole saga work? | `make smoke` | `make k8s-smoke` (needs `make k8s-forward` running) |

Grafana (`:3000`) → "E-Commerce Platform — Saga Overview" is the first stop for
"is it working": orders/min, payment success rate, consumer lag, DLQ count,
saga anomalies.

## Recovery after a WSL / Docker restart

Both stacks survive restarts — data lives in volumes/PVCs — but neither
auto-starts:

- **Compose**: `make up` again. Kafka and Mongo containers keep their volumes.
- **kind**: the cluster's node container must be started manually:

  ```bash
  docker start ecommerce-control-plane
  kubectl -n ecommerce get pods -w   # services restart until Mongo is ready — expected
  ```

  Service pods may show several restarts immediately after: they crash-loop
  until Kafka/Mongo pass readiness. That is the intended fail-fast behavior,
  not an incident.

## Tracing a request / saga

Every HTTP request gets a correlation ID (or supply your own via the
`X-Correlation-Id` header). It propagates through every event and appears in
every JSON log line of every service it touches.

```bash
# Compose
docker logs <svc> 2>&1 | grep <correlation-id>
# Kubernetes
kubectl -n ecommerce logs deploy/<svc> | grep <correlation-id>
```

To see where an order stopped: `GET :8081/api/v1/orders/{id}` — the status
(`CREATED/PAID/FAILED/CANCELLED`) plus `statusReason` tell you which saga leg
failed; then grep the correlation ID in the implicated service.

## Dead-letter queues

A message lands on `<topic>.DLT` after retries are exhausted or immediately for
non-retryable errors (e.g. deserialization). The Grafana DLQ panel (backed by
`kafka_dlq_messages_total`) tells you *that* it happened; the DLT record tells
you *why* — diagnostic headers include the exception class
(`kafka_dlt-exception-cause-fqcn`) and origin topic/partition/offset.

```bash
# Compose (kind: prefix with kubectl -n ecommerce exec kafka-0 --)
docker exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic order-created.DLT \
  --from-beginning --property print.headers=true
```

Redelivery is manual by design: fix the cause, then re-produce the record to
the original topic with `kafka-console-producer.sh`. Consumers are idempotent
(dedup by `eventId`), so re-producing something that half-processed is safe.

## Common symptoms

| Symptom | Likely cause → action |
|---|---|
| Service pod restarts repeatedly at startup | Kafka/Mongo not ready yet → wait; check `kubectl describe pod` events if it persists past ~3 min |
| `mongodb-0` init crash-loops with `Permission denied` | Stale keyfile in emptyDir — fixed (initContainer now `rm -f`s first); if seen on an old revision, `kubectl delete pod mongodb-0` |
| Kafka pod restarts with cluster-ID mismatch | `CLUSTER_ID` changed while the PVC kept old `meta.properties` → restore the pinned ID, or delete the PVC (loses topics) |
| Order stuck in `CREATED` | Outbox relay or Kafka down → check `outbox_pending` gauge in Prometheus, then order-service logs |
| Payment always fails for one order | Amount ending `.99` is the *deterministic* decline rule (by design, for testing) |
| Consumer lag rising | Check the consuming service's logs for retry storms; a poison pill will end up on the DLT — see above |
| `saga_anomalies_total` > 0 | An event arrived in an unexpected order-state — grep the correlation ID; the state machine ignored it safely, but investigate why |
| Smoke test fails only at the log-correlation step | Wrong log source for the environment — `make smoke` (docker) vs `make k8s-smoke` (kubectl) |

## Resetting state

```bash
# Compose: full reset (deletes Mongo + Kafka volumes)
make clean && make build up

# Kubernetes: recreate everything but keep the cluster
kubectl delete ns ecommerce && make k8s-apply

# Kubernetes: nuke the cluster
make k8s-down && make k8s-cluster k8s-images k8s-apply
```
