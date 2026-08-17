# ADR-001: JSON-First Configuration

## Status

Accepted

## Context

Kestrel needs portable configuration for layouts, skins, profiles, controller definitions, aspect-ratio presets, and community content.

## Decision

Use JSON as the canonical portable configuration representation wherever practical. Platform-specific persistence may still be used for small runtime preferences where appropriate.

## Rationale

JSON provides portability, inspectability, easy import/export, Git-friendly diffs, community sharing, AI-friendly implementation, and schema versioning.

## Consequences

Positive:
- portable user data
- community-friendly configuration
- easy validation and migration

Negative:
- validation is required
- schema migration is required as formats evolve
- unusually large runtime data may eventually need another storage mechanism

Any move away from JSON-first requires a new decision record.
