# Kestrel — Release Criteria

**Document:** `docs/RELEASE.md`  
**Status:** Active — what `v0.1.0` requires, set by the project owner  

---

## 1. What a release means here

Every artifact this repository has produced so far is a **testing build**: signed with a key
committed to the repository (`signing/README.md`), described as such wherever it is published, and
intended for one device and one person.

A release is different in exactly one way that matters — it is offered to people who were not in the
room while it was made. Everything below follows from that.

## 2. The gate for `v0.1.0`

Set by the project owner: tag and publish `v0.1.0` to `main` once **four** things are finished, with
no conflicts, errors or known defects outstanding.

| Requirement | Phase | State |
| --- | --- | --- |
| Overlay | 2 | Working on the reference device; not yet rendering from the layout document |
| Controller editor | 3 | Not started — unblocked now that a layout is data |
| Gaming session | 4 | A session holds a controller; it cannot launch a target or load a profile |
| Shizuku | 5 | Detection, permission, privilege level, UserService, capability reporting — done |

"No conflicts, errors, bugs" is read as: CI green on the branch, no known defect the project owner
has reported and not seen fixed, and every claim in `CHANGELOG.md` still true.

## 3. What must also be true before a tag

These are not extra scope. They are the difference between a build for one person and a build for
anyone, and none of them is currently done.

- **A release signing key that is not in this repository.** The committed key exists so that a new
  testing build installs over the last one instead of forcing an uninstall. It is public, so anyone
  can sign an application that installs as an update over a user's Kestrel. That is acceptable for
  builds people are testing deliberately and unacceptable for builds people are trusting.
  `signing/README.md` §"What it is not" carries the steps.
- **The release notes say what the build is and is not.** `ADR-INPUT-001` is Accepted *scoped to one
  device*. A release that does not say so is claiming compatibility nobody measured.
- **`docs/COMPATIBILITY.md` reflects what has actually been tested**, with device, firmware, version
  and result — not what is expected to work.
- **Everything shipped has its licence on the record** in `THIRD_PARTY_LICENSES.md`, including
  artwork.
- **The changelog is the release notes.** It records what was established, including what was
  established to be false, and that is what a first release should be honest about.

## 4. How a tag becomes a release

Already built. `.github/workflows/build.yml` publishes a release with both APKs attached on any tag
matching `v*`, which gives a stable link that can be opened on a phone.

```bash
git tag v0.1.0
git push origin v0.1.0
```

The workflow's release notes currently describe both artifacts as debug-signed builds for testing.
**That wording is correct today and must change when §3's signing item is done** — not before, and
not by editing the text alone.

## 5. What `v0.1.0` will not be

Worth writing down so the number is not read as more than it is.

- Not tested on any device but the reference one.
- Not usable without Shizuku for input (`ADR-006`, Rejected — measured, works, not worth shipping).
- Without skins, a community system, or target discovery, which are Phases 6, 7 and 1.

`PRD.md` §35's MVP is a larger thing than this gate: it requires seeing targets, selecting one, and
launching it. `v0.1.0` is the point at which the controller half of that flow is finished and
honest, which is a real milestone and not the whole product.
