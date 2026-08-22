# Kestrel — Where Files Live

**Document:** `docs/STORAGE.md`  
**Status:** Active — decided, implemented, not yet device-tested  

---

## 1. The decision

**Everything Kestrel keeps lives in a folder the user chooses, at the top level of shared storage —
beside `Android`, not inside it.**

Layouts, skins, profiles, settings and exported reports all go there. The folder survives
uninstalling Kestrel, opens in any file manager, and can be copied to a computer or another phone
like any other folder.

Requested by the project owner, and the reasoning holds up on its own: an application's private
directory is deleted with the application, and on a modern phone `Android/data` cannot be browsed at
all. Work a person spent an evening on should not be somewhere they cannot see and cannot keep.

## 2. How the folder is reached, and what was rejected

Two ways exist to write outside an application's own directory.

| | Storage Access Framework | `MANAGE_EXTERNAL_STORAGE` |
| --- | --- | --- |
| What it grants | The one folder the user picked | Every file on the phone |
| How it is granted | A folder picker | A restricted permission, in a settings screen |
| Declared in the manifest | Nothing | A restricted permission |
| Install-time cost | None | Unknown, and `ADR-006` measured what a manifest declaration can cost |
| Survives uninstall | The **folder** does; the grant does not | Same |

**The picker is used.** It grants less, needs nothing declared, and asks the user a question whose
answer is exactly the folder they wanted. The alternative asks for access to everything in order to
write to one place — and having just watched a manifest declaration turn Kestrel into an application
Play Protect blocks outright, a second restricted declaration is not a thing to add on a hunch.

One consequence to be honest about: on Android 11 and later the picker **will not let anyone select
the root of shared storage itself**. A folder inside it — `Kestrel` — is selectable, which is what
was wanted anyway.

## 3. Never required

With no folder chosen, Kestrel keeps working and uses its own directory. The screen says where the
files are and that they will not survive an uninstall, and offers one action.

That follows `docs/DEGRADED_STATE.md` §2: the application does not refuse to start, and does not
hide its own features, because something is unavailable. A user who never answers the question still
has a working product; they have simply not been quiet about the cost.

**Choosing a folder copies what is already there into it.** Somebody who has been using Kestrel has
settings, and starting them again from defaults as a reward for answering a question would be a
punishment for answering it.

## 4. What is inside the folder

```text
Kestrel/
  settings.json      one per installation, readable and editable by hand
  layouts/           controller layouts, built-in copies and your own
  skins/             artwork
  profiles/          per-target configuration
  reports/           exported diagnostics
```

Flat, and the folder names are a fixed list. That is a security property rather than a
simplification: documents can be **imported**, and an imported document that could choose its own
path could choose one outside the folder. There is no path to validate because there is no path — a
caller names a folder from the list and a document within it.

Document names are checked before they reach a filesystem: no separators, no `..`, no control
characters, nothing longer than 96 characters, and none of the names Windows reserves — because
copying the folder to a computer is a supported thing to do, and a file that cannot be copied is one
that quietly does not get backed up.

## 5. Why settings are a document

`settings.json` is a configuration document with a schema version, not a private key-value store,
and it sits in the folder with everything else. It can be read, edited, copied to another phone, and
kept when Kestrel is uninstalled. **Settings that can only be changed from inside the application
are settings that disappear with the application**, which is the problem this exists to end.

Two rules the implementation keeps:

- **A file that cannot be read is left alone.** Kestrel runs on defaults, says so, and refuses to
  save over it. A file that failed to parse may be one the user can fix; replacing it with defaults
  would destroy the only copy while looking like recovery.
- **Fields Kestrel does not recognise are written back.** A settings file from a newer build, read
  and saved by an older one, keeps what the older one did not understand.

## 6. What is measured and what is not

| Claim | State |
| --- | --- |
| The store's promises — round trip, folder separation, name rules, size limit | **Tested** — 181 `:core` tests, on a memory store |
| Settings round-trip through a store, with defaults, partial files and unknown fields | **Tested** |
| The private-directory store on a device | **Untested** |
| The picker, the grant, and the chosen folder on a device | **Untested** |
| That the folder survives uninstalling Kestrel | **Untested** — expected, and the whole point |
| That the grant does *not* survive uninstalling Kestrel | **Expected** — the folder must be picked again, the files are still there |
