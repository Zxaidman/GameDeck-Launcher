# Third-Party Licenses

**Document:** `THIRD_PARTY_LICENSES.md`  
**Status:** Active — versions pending first dependency resolution  

## Purpose

Kestrel is licensed under GNU GPLv3, but the project may depend on third-party libraries, frameworks, tools, and services with their own licenses.

This document is the repository-level index for those dependencies.

It does **not** replace the actual license text supplied by each dependency. The project must preserve required notices and comply with the license of every dependency it distributes or uses.

---

## License Review Rules

Before adding a dependency, contributors should verify:

1. Exact dependency name.
2. Exact version.
3. License.
4. Whether the license is compatible with the way Kestrel is distributed.
5. Attribution/notice requirements.
6. Whether the dependency introduces additional runtime dependencies.
7. Whether it introduces unnecessary permissions, native code, telemetry, or other project risk.

If the license is unclear, do not merge the dependency until it has been reviewed.

---

## Dependencies In Use

| Dependency | Version | Licence | Why |
| --- | --- | --- | --- |
| `dev.rikka.shizuku:api` / `:provider` | see `gradle/libs.versions.toml` | Apache-2.0 | The privilege `ADR-INPUT-001`'s backend needs. Optional at runtime (`ADR-003`) |
| `androidx.documentfile:documentfile` | 1.1.0 | Apache-2.0 | Reading and writing inside the folder the user chose (`docs/STORAGE.md`) |
| AndroidX core, lifecycle, activity-compose, Compose BOM | see `gradle/libs.versions.toml` | Apache-2.0 | Platform and interface |
| JUnit 5 | see `gradle/libs.versions.toml` | EPL-2.0 | Tests only, not distributed |

Kotlin and the Android Gradle Plugin are build tooling rather than dependencies of the artifact.

---

## Bundled Assets

Artwork and other non-code material distributed with Kestrel, or held in the repository pending a
decision. Tracked here for the same reason dependencies are: what ships must have terms on the
record.

### Xelu's Free Controller Prompts

| | |
| --- | --- |
| **Author** | Nicolae "Xelu" Berbece — Those Awesome Guys |
| **Licence** | **CC0 1.0** — public domain dedication |
| **Licence text** | `docs/inbox/skins/LICENSE.txt`, as shipped with the pack |
| **In the repository at** | `docs/inbox/skins/` — 233 files, 256×256 PNG |
| **Distributed in Kestrel** | **Not yet.** Assessed and cleared; moves to `data/` when a skin format exists (`docs/SKIN_ASSETS.md`) |

CC0 imposes no notice or attribution requirement, and there is no copyleft conflict with GPLv3. The
author asks to be credited and says explicitly that he does not mind if he is not. **Kestrel credits
him anyway** — taking someone's work and not naming them is a choice about this project rather than
about the licence, and the licence permitting it does not make it the right thing to do.

Attribution appears wherever a skin drawn from this pack is shown, and here.

*Separate from the licence:* the pack draws shapes associated with hardware vendors, which is a
trademark question that a licence on the files does not answer. `docs/SKIN_ASSETS.md` records the
position. Kestrel presents its own device identity rather than another vendor's
(`docs/CONTROLLER_FAMILIES.md` §2), so a skin drawing familiar glyphs is not a device claiming to be
someone else's hardware.

---

## Dependency Categories

The project should track at least:

- Android/Jetpack libraries
- Kotlin libraries
- Shizuku
- Build plugins
- Testing libraries
- Native libraries
- Image/asset libraries
- Community/content tooling

Gradle transitive dependencies should also be reviewed when practical.

---

## Current Planned / Known Dependencies

> Versions are intentionally marked `TBD` until the actual Gradle project is established.

| Dependency | Purpose | Version | License | Attribution / Notice | Notes |
|---|---|---|---|---|---|
| Kotlin | Primary language | TBD | Apache-2.0 | Yes | Verify exact distribution/version |
| AndroidX / Jetpack Compose | Android UI/platform libraries | TBD | Apache-2.0 | Yes | Track actual modules used |
| Shizuku API | Shell-privilege access for the Phase 0 harness only | 13.1.5 | Apache-2.0 | Yes | `dev.rikka.shizuku:api`. Used by `tools/phase0` only; never by `:app` or `:core`, verified by inspecting the product's runtime classpath |
| Shizuku Provider | Companion provider required by the Shizuku API | 13.1.5 | Apache-2.0 | Yes | `dev.rikka.shizuku:provider`. Same restriction |

---

## Shizuku

Project:

https://github.com/RikkaApps/Shizuku

Shizuku is expected to be an optional integration rather than a mandatory dependency.

The project includes components under Apache License 2.0. The exact components and version used by Kestrel must be verified when the dependency is added.

Kestrel must preserve applicable copyright and license notices.

Do not copy large portions of Shizuku source into Kestrel merely for convenience. Prefer the documented dependency/API boundary.

---

## AndroidX / Jetpack

AndroidX and Jetpack components are generally distributed under Apache License 2.0.

The actual modules used by Kestrel should be recorded once the Gradle project exists.

Example future entries:

```text
androidx.activity
androidx.compose.*
androidx.lifecycle
androidx.navigation
androidx.test.*
```

The project should track the exact modules actually included rather than claiming a dependency on all of AndroidX.

---

## Kotlin

Kotlin tooling and runtime components should be tracked according to the exact version and artifacts used by the project.

Do not assume that every artifact associated with the Kotlin ecosystem has identical licensing without verification.

---

## Runtime vs Development Dependencies

Not every development dependency needs to become an application runtime dependency.

Examples:

```text
Build plugins
Test libraries
Lint tools
Code-generation tools
```

should be tracked separately where useful.

---

## Native Dependencies

Native code introduces additional legal, security, and maintenance concerns.

Any native dependency must record:

- source
- version
- license
- target architectures
- included native libraries
- license notices
- security considerations

Do not add native dependencies casually.

---

## Fonts, Icons, and Visual Assets

Third-party visual assets must be tracked separately from source-code dependencies.

This includes:

- fonts
- icons
- textures
- controller artwork
- illustrations
- sounds

Every asset must have documented redistribution permission.

Do not include copyrighted controller artwork or third-party assets merely because they are available online.

---

## Community Content

Community-created layouts and skins are not automatically Kestrel-owned.

Community content should preserve its own:

- author information
- license
- attribution
- source where appropriate

The application should not silently relicense third-party community content as Kestrel code.

---

## License Compatibility

Kestrel's core project license is GPLv3.

A third-party dependency being “open source” is not sufficient to establish compatibility.

Before adding a dependency, determine:

- whether linking/combining it is permitted
- whether redistribution is permitted
- whether source disclosure obligations apply
- whether attribution is required
- whether additional notices are required

For unusual or uncertain cases, seek qualified legal advice.

---

## Required Repository Notices

When needed, the final application/distribution should provide the required third-party notices through an appropriate location such as:

```text
Settings
  ↓
Open Source Licenses
```

and/or a repository file such as:

```text
THIRD_PARTY_NOTICES.md
```

The exact presentation will be decided when the release/distribution system is implemented.

---

## Dependency Review Checklist

Before merging a new dependency:

- [ ] Dependency name recorded.
- [ ] Exact version recorded.
- [ ] License verified from authoritative source.
- [ ] Compatibility with GPLv3 distribution reviewed.
- [ ] Attribution requirements recorded.
- [ ] Security/maintenance risk reviewed.
- [ ] Android compatibility reviewed.
- [ ] Unnecessary dependency duplication avoided.
- [ ] Third-party notice plan identified.

---

## Important Principle

> **Do not treat licensing as an afterthought.**

A dependency can affect the legal distribution of the whole application.

The project should prefer a small, understandable dependency set and should keep this document updated as the project evolves.
