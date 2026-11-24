# P6.2 · Qualifying a hosted runtime

**Experimentally evaluated on Docker Cloud Micro, 23 November 2026.** A container restricts processes while sharing its kernel. A microVM adds a guest kernel. Provider policy, attached credentials and reusable disks still determine which data crosses tenants. A fast create call gives no evidence about any of those controls.

Creation took 10.27 seconds and the median of three subsequent CLI round trips was 2.598 seconds. The guest reported a DNS failure. The matching provider audit entry attributed eight DNS denials for the synthetic destination to the per-sandbox rule. The combination establishes enforcement for that specific destination. A single disposable environment cannot demonstrate isolation between two customers. Docker confirmed removal and the resource disappeared from inventory. Public tenant execution stays gated until the [qualification criteria](../modules/phase-six-runners.md) pass.
