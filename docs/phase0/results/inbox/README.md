# Evidence inbox

**Document:** `docs/phase0/results/inbox/README.md`  
**Status:** Active — drop-off point for raw harness exports  

Put harness exports here. Nothing else.

## Why this exists

Sending an export through a chat window costs tokens every time and the file has to be re-uploaded
for each run. Pushing it here instead means the file is already in the repository, and the analysis
step is "read `docs/phase0/results/inbox/`" rather than "please upload it again".

## How to use it

1. In the harness, press **Save…** (choose a folder you can reach) or **Share** and send it to
   yourself. Both produce a `.json` file.
2. Copy that file into this folder in the repository and push it. The file name the harness
   generates is fine — it does not need renaming first.
3. Say which run it was in one line: what you pressed, what you saw, anything that looked wrong.
   That one line is worth more than the file, because the file cannot tell anyone what you
   expected to happen.

## What happens to a file dropped here

It gets read, checked against what it was supposed to prove, renamed to the naming convention in
`docs/phase0/README.md` §6:

```text
docs/phase0/results/<tier>-<yyyymmdd>-<device>.json
```

and moved one directory up, next to a short report summarising it. **This folder is a staging
area, not an archive** — files here are temporary and get moved out. The permanent record lives in
`docs/phase0/results/` with a report beside it.

If a file here is left in place, it means it has not been processed yet.

## What not to put here

- Screenshots. They are useful in chat but they are not evidence records; the export contains the
  same facts in a form that can be checked.
- Anything that is not a harness export. This folder is read on the assumption that everything in
  it is one.

## A note on what these files contain

The export carries the device fingerprint, build id, security patch level, the full input device
inventory, and the event log. It carries no account identifier, no location, no screen content, and
nothing typed outside the harness. It is safe to commit to a public repository — the fingerprint
identifies a *model and firmware build*, which is exactly what `docs/COMPATIBILITY.md` needs, not
a person.
