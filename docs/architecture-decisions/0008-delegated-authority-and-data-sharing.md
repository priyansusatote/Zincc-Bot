# ADR 0008 — Explicit delegated authority and output audiences

Date: **16 November 2026**. Status: **Proposed**.

## Problem

A team member can invoke a shared agent owned by someone with more access. If the agent simply inherits its owner's connections, a harmless question can become a private-mail leak or unauthorized write. A parent/child graph and a valid OAuth token do not solve this.

## Decision

Every run records initiator, effective principal, workspace, delegation/grant chain, agent version, trigger, destination audience and policy revision. An interactive run defaults to the intersection of caller access, agent grants, connection resource grants and workspace policy. Scheduled/service agents use an explicitly assigned service principal or owner delegation; their permitted recipients and operations are declared in the standing grant. They do not inherit whichever human happens to open their chat.

Children can narrow inherited grants, not expand them. Cross-agent collaboration sends authorized artifact references and typed findings, not whole private transcripts or credentials. Resource scope and output audience are checked before context assembly, tool dispatch and delivery. Sharing an agent definition does not share its owner's credentials, memories or prior conversations.

Derived artifacts inherit source restrictions by default. An explicit publication/release action may create a new approved audience-bound artifact after reviewing its contents; source citations do not silently grant access. Removing a source grant blocks future regeneration and internal access according to policy, but cannot recall an already-delivered external copy. Record that distinction.

Workspace admins manage membership and policies; access to private content requires an explicit role/grant, not an implicit administrative shortcut. Offboarding revokes delegated execution, refresh credentials where applicable, outstanding approvals and device grants; ownership transfer is a deliberate operation.

## Alternatives

- Run every request as the agent owner: simple but amplifies caller privileges.
- Require every recipient to access every source forever: safe for private artifacts but prevents deliberate authorized publication.
- Trust prompts to keep private facts secret: model behavior is not an authorization boundary.
- Introduce a separate relationship database immediately: unnecessary for initial scoped role/resource grants; evaluate later if relationship complexity warrants it.

## Trade-offs and consequences

The policy model is more explicit and sharing requires audience-aware UX. Context caches need an access fingerprint and revocation checks. Publication has irreversible disclosure implications. The benefit is one enforceable model for personal, team, scheduled and multi-agent work.

## Validation

Test a lower-privilege caller invoking an owner's shared agent, a child requesting broader scope, private memory in a shared conversation, offboarding during an approval wait, cache hits after ACL change, and artifacts combining differently restricted sources. Authorization correctness starts in P1.2/P1.3 and P3.3, not final P12 administration.
