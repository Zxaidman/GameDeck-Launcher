# ADR-004: Android 10+ Baseline

## Status

Accepted

## Context

GameDeck needs a modern Android baseline while keeping initial device testing manageable.

## Decision

Minimum supported Android version: Android 10 / API 29.

Initial device target: phones only. Tablets and foldables are deferred until the phone experience is stable.

## Consequences

Positive:
- clear support target
- less legacy compatibility work
- simpler initial testing

Negative:
- Android 9 and older are excluded
- later Android versions still require version-specific handling
