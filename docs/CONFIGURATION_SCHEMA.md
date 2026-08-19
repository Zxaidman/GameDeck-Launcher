# Kestrel — Configuration Schema

**Document:** `docs/CONFIGURATION_SCHEMA.md`  
**Status:** Active — schema version 1, no implementation yet  

## Purpose

Kestrel is JSON-first. Configuration should be portable, inspectable, versioned, exportable, migration-friendly, safe to import, and independent of UI code or a specific input backend.

## Configuration types

Initial conceptual types:

- controller-definition
- controller-layout
- controller-skin
- gaming-profile
- application-record
- aspect-ratio-preset
- community-manifest
- compatibility-record

## Common fields

```json
{
  "schemaVersion": 1,
  "type": "controller-layout",
  "id": "example.id",
  "name": "Example"
}
```

Optional metadata may include version, author, description, license, tags, source, sourceTemplate, createdAt and updatedAt.

## Schema versioning

Every schema has an explicit version.

- incompatible changes require a new schema version
- migrations should be explicit
- unsupported future versions fail safely
- old configurations must not be silently reinterpreted

## Stable IDs

IDs are distinct from display names. Examples:

```text
builtin.xbox.default
builtin.ps.default
user.<uuid>
profile.<uuid>
skin.<uuid>
```

Renaming must not require changing a stable ID.

## Controller definition

Defines logical controls, not screen positions.

```json
{
  "schemaVersion": 1,
  "type": "controller-definition",
  "id": "controller.xbox",
  "name": "Xbox-style Controller",
  "buttons": ["A", "B", "X", "Y", "LB", "RB", "START", "BACK"],
  "axes": ["LEFT_X", "LEFT_Y", "RIGHT_X", "RIGHT_Y"],
  "triggers": ["LT", "RT"]
}
```

## Controller layout

Defines arrangement and mapping.

```json
{
  "schemaVersion": 1,
  "type": "controller-layout",
  "id": "builtin.xbox.default",
  "name": "Xbox Default",
  "builtin": true,
  "editable": false,
  "controllerDefinition": "controller.xbox",
  "elements": []
}
```

## Built-in layouts

Built-ins are immutable. Repository/domain code must reject attempts to overwrite them.

```text
Built-in → Duplicate → User layout → Edit
```

## Layout elements

May define:

- id
- control
- x/y
- width/height
- rotation
- visibility
- opacity
- mapping
- behavior
- anchors

### `control` and capability

`control` names what the element **is** — `button`, `dpad`, `stick`, `analog-trigger`,
`digital-trigger`, `decoration`. What a backend must provide for it to work is **derived from that
kind**, never stored in the document.

That is deliberate. Storing the requirement would freeze today's understanding of capability into
every file ever exported, and a document written now would mean the wrong thing after the capability
model gains a distinction. Storing the kind means a layout keeps meaning what its author meant.

`ADR-007` decides what happens when the active backend cannot provide it: the element is **shown and
disabled**, never removed, never substituted. `digital-trigger` exists as a separate kind for the
same reason — a user may choose a digital trigger, and it then works where an analog one cannot, but
the product never performs that substitution on their behalf.

Coordinates should use a device-independent or normalized representation rather than one phone's raw pixels as the canonical source.

### How position and size are normalised, and why differently

Normalising alone is not enough: a layout built on a 20:9 phone and opened on a squarer screen has
to stay *playable*, and both naive approaches fail. Normalising position against full width and
height moves a thumb-reachable control towards the middle of a wider screen. Normalising size
against width and height independently turns a round button into an ellipse.

So the two are normalised differently, on purpose:

- **Position** is an offset from an **anchor** — one of nine points on the surface. A control pinned
  to the bottom-left corner stays where a thumb rests, whatever the screen becomes. Offsets are
  applied *inwards* from the anchor, so an author never writes a negative number to move a
  right-hand control away from the right edge.
- **Size** is measured against the surface's **shorter side only**, so a control keeps its shape and
  its size relative to the hand holding the phone — and rotating the phone does not resize anything.

Both are in the same unit, so a control and its offsets scale together and an arrangement holds its
proportions.

### Insets

The usable surface excludes display cutouts and gesture areas. Those are device-specific, which is
exactly why a layout must not encode them: the surface subtracts them, and the same layout lands
correctly on a phone with a cutout and one without.

A control that falls outside the usable area is **reported, not corrected**. Running a control off
an edge can be a deliberate design, and the same principle as `ADR-007` applies — the product says
what it sees rather than overruling the author.

### Rotation

Rotation is part of hit testing, not only of drawing. A touch is tested by rotating the *point* back
around the control's centre and comparing against an upright rectangle, which is exact. A rotated
control's bounding box is larger than its own width and height, and the bounding box is used only as
an editor hint about overlap — never to decide which control receives a touch.

## Skin

A skin defines appearance only.

```json
{
  "schemaVersion": 1,
  "type": "controller-skin",
  "id": "builtin.minimal.dark",
  "name": "Minimal Dark",
  "assets": {},
  "styles": {}
}
```

A layout must not depend on one skin.

## Gaming profile

```json
{
  "schemaVersion": 1,
  "type": "gaming-profile",
  "id": "profile.1234",
  "name": "Moonlight Xbox",
  "application": {"packageName": "example.package"},
  "layout": "builtin.xbox.default",
  "skin": "builtin.minimal.dark",
  "display": {
    "orientation": "LANDSCAPE",
    "scalingMode": "FIT",
    "aspectRatio": "16:9"
  },
  "input": {}
}
```

## Manual application record

```json
{
  "packageName": "com.example.game",
  "source": "USER",
  "preferredProfile": "profile.1234"
}
```

## Aspect ratios

Aspect ratios are data:

```json
{
  "schemaVersion": 1,
  "type": "aspect-ratio-preset",
  "id": "16:9",
  "width": 16,
  "height": 9,
  "builtin": true
}
```

Initial presets include 4:3, 16:9, 18:9, 19.5:9, 20:9 and 21:9.

## Community manifests

Community repositories should expose declarative metadata such as:

```text
id, type, name, author, version, license,
download, checksum, minimumKestrelVersion,
compatibility, preview
```

Community content must not be executable.

## Validation

Every import must validate:

- schema version
- required fields
- field types
- ID format
- numeric ranges
- enum values
- collection sizes
- references
- file limits

Invalid data must produce a typed error and must not crash the application.

The typed errors are `ConfigurationError` in `core/configuration/`, and each names the field it
concerns. A user handed someone else's layout and told only that it is invalid has no way forward;
told that `elements[3].opacity` is 1.4 and must be between 0 and 1, they can fix it or report it
usefully.

Two ordering rules, because they change what the other checks mean:

- **Schema version is checked first.** A document from a future version is not malformed — this
  build is simply older — and it must be reported as such rather than as an invalid file.
- **Document type is checked before any type-specific field.** Reading a skin as a layout should
  say so, not fail later on a missing field that was never going to be there.

## Unknown fields

Unknown non-executable fields should ideally be preserved where safe to improve forward compatibility.

Implemented: validation reads from the parsed document rather than consuming it, and every header
carries the fields it did not recognise. A document written by a newer build at the same schema
version keeps those fields when this build re-exports it, instead of quietly losing them.

## Export/import

Users must be able to export and re-import their own layouts, skins, profiles, and applicable configuration.

## Security boundary

Configuration is data. Do not introduce fields that execute shell commands, arbitrary code, native libraries, or downloaded plugins without a separate security design.
