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

Coordinates should use a device-independent or normalized representation rather than one phone's raw pixels as the canonical source.

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

## Unknown fields

Unknown non-executable fields should ideally be preserved where safe to improve forward compatibility.

## Export/import

Users must be able to export and re-import their own layouts, skins, profiles, and applicable configuration.

## Security boundary

Configuration is data. Do not introduce fields that execute shell commands, arbitrary code, native libraries, or downloaded plugins without a separate security design.
