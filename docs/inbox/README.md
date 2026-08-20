# Inbox — where to push things for Kestrel to look at

**Document:** `docs/inbox/README.md`  
**Status:** Active — the one drop-off point  

Anything you want assessed goes here. One place, three folders, no guessing.

| Folder | What goes in it |
| --- | --- |
| `docs/inbox/reports/` | Exported `.json` diagnostic reports from the application |
| `docs/inbox/ideas/` | Notes, proposals, sketches — Markdown and images together |
| `docs/inbox/skins/` | Artwork: controller prompts, backgrounds, anything visual |

## Why this exists rather than a chat attachment

Sending a file through a chat window costs tokens every time and has to be re-uploaded for every
run. Pushing it here means the file is already in the repository, and the step becomes "read
`docs/inbox/`" instead of "please upload it again". It also means the material is versioned: what
was assessed, and when, is recoverable later.

## What the inbox is not

**Not a distribution path.** Nothing here ships. An idea that is adopted becomes a document under
`docs/` or a decision under `docs/adr/`; artwork that is adopted moves into `data/` with a manifest
and a licence on the record. Material stays in the inbox until it has been through that, which is
also what keeps unlicensed or unassessed content out of the product by construction.

**Not a queue.** Nothing is deleted from here on being assessed — the record of what arrived is
worth keeping.

## Pushing ideas

`docs/inbox/ideas/` takes a folder per idea, or a loose `.md` if it is small. Images alongside the
Markdown, referenced with relative links, so it reads correctly on GitHub:

```text
docs/inbox/ideas/
  launcher-home-screen/
    notes.md
    sketch-1.png
    sketch-2.png
  quick-thought.md
```

Nothing about the format matters beyond that. Rough is fine — the point is that it arrives whole,
with the pictures attached to the words that explain them.

## Pushing reports

In the application, press **Clear trail**, do the thing being tested, then **Save…** or **Share**.
Copy the `.json` here. The generated name is fine and does not need renaming. Say in one line what
the run was: what you pressed, what you saw, anything that looked wrong.

## History

This replaces `docs/phase0/results/inbox/`, which was named for Phase 0 and outlived it. Its
contents moved here.
