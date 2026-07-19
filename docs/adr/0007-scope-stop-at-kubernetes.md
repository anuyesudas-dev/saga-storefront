# ADR-0007: Stop at Kubernetes — defer OpenShift and load testing

**Status:** accepted (2026-07-19)

## Decision
The deployment story ends at the kind Kubernetes cluster (Phase 16). The
planned OpenShift Local phase and the dedicated load-testing phase are
deferred, not built.

## Rationale
OpenShift Local (CRC) cannot run inside the WSL2 development environment — it
boots its own VM needing ~11 GiB, and the host lacks the headroom. Faking it
(unverified manifests against no cluster) would violate the project's rule that
every phase ends verified. The k8s deployment already proves the
production-shaped concerns: probes, Secrets, non-root containers with dropped
capabilities, StatefulSet persistence, RBAC. Load testing was cut for scope:
the metrics pipeline it would exercise (latency histograms, consumer lag,
saga throughput) is already in place and observable.

## What an OpenShift port would require
Recorded so the path stays concrete: Routes instead of port-forwards;
`restricted-v2` SCC compliance (drop the fixed `runAsUser`/`fsGroup` — pods get
arbitrary UIDs; Mongo's official image then needs an `anyuid`-granted service
account or an OpenShift-friendly image); image delivery via the internal
registry instead of `kind load`; ideally a base/overlay Kustomize refactor so
OpenShift is a patch set, not a fork. The current containers already run
non-root with dropped capabilities, which covers most of the SCC distance.

## Alternatives
Running CRC on the Windows host (needs 16 GiB+ and Hyper-V) remains open as a
later exercise; nothing in the repo blocks it.
