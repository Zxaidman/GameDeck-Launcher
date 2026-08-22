# ADR-003: Shizuku Is Optional

## Status

Accepted

## Context

Shizuku may expose capabilities unavailable to a normal application, but available capabilities vary by privilege level and device.

## Decision

Shizuku is an optional capability provider. Kestrel should remain useful without it where normal Android APIs or fallbacks can provide the required behavior.

## Rationale

Making Shizuku mandatory would unnecessarily reduce accessibility and couple the product to an external component.

## Consequences

Positive:
- broader compatibility
- optional power-user capabilities
- no mandatory Shizuku dependency

Negative:
- multiple capability paths
- more compatibility testing
- some features may differ by device

Kestrel must detect actual capabilities rather than only checking whether Shizuku is installed.
