# Security Policy

## GameDeck Android

GameDeck is an open-source Android gaming project intended to provide a unified gaming launcher, virtual controller, game profiles, skins, and configuration system for Android phones.

Security is especially important because GameDeck may eventually interact with:

- Android overlay functionality
- Shizuku
- privileged system APIs
- input-related system functionality
- third-party applications
- downloaded community layouts and skins
- locally stored configuration
- future community repositories

This document explains how security issues should be reported and how security is approached during development.

---

# 1. Security Philosophy

GameDeck follows a simple principle:

> **Use the minimum privileges necessary to provide the required functionality.**

A feature should not request elevated privileges merely because elevated privileges make implementation easier.

In particular:

```text
Normal Android API
        ↓
Preferred

Shizuku / elevated capability
        ↓
Only when required

Root
        ↓
Experimental / exceptional
```

The project will clearly document why elevated access is needed when a feature requires it.

---

# 2. Security Is a Development Requirement

Security is not something that will be added after the application is complete.

Security considerations should be included when designing:

- input backends
- Shizuku integration
- overlays
- configuration import/export
- community repositories
- profile loading
- skin loading
- update mechanisms
- future plugin systems

---

# 3. Threat Model

Potential security risks include, but are not limited to:

### Privileged input abuse

A malicious or compromised component could potentially abuse system-level input capabilities.

### Shizuku misuse

A vulnerability in a Shizuku integration could cause GameDeck to perform unintended privileged operations.

### Malicious community configurations

A malformed or malicious JSON file could attempt to exploit a parser or trigger unintended application behavior.

### Malicious skins

Image or resource processing could potentially expose vulnerabilities.

### Package/application manipulation

Incorrect application detection or package handling could cause GameDeck to launch or interact with an unintended application.

### Update/repository compromise

A compromised community repository could distribute malicious configuration or assets.

### Sensitive diagnostics

Logs could accidentally contain information that should not be exposed.

---

# 4. Community Content Is Untrusted

GameDeck must treat all downloaded community content as untrusted.

This includes:

- layouts
- skins
- profiles
- manifests
- compatibility data
- images
- metadata

Downloaded files must never be trusted simply because they came from a configured GitHub repository.

---

# 5. Declarative Content Only

The initial community system should use declarative formats.

Examples:

```text
JSON
Images
Metadata
```

Community content must not execute arbitrary code.

The initial design should not support:

- downloaded scripts
- downloaded native libraries
- arbitrary shell commands
- executable plugins
- remote code
- configuration that directly invokes system commands

A future plugin system, if ever considered, requires a separate security design and review.

---

# 6. JSON Validation

All imported JSON must be treated as untrusted input.

Validation should include:

- schema validation
- schema version checking
- type checking
- maximum file size
- maximum nesting depth where appropriate
- bounded collection sizes
- string-length limits
- numeric-range validation
- identifier validation

Malformed input should fail safely.

GameDeck must not crash or enter an unsafe state because a user imported a malformed configuration.

---

# 7. Path and File Safety

Imported files must not be able to escape their intended storage location.

The implementation must protect against:

- path traversal
- unexpected absolute paths
- symbolic-link abuse where applicable
- overwriting unrelated files
- unsafe temporary files

Community content should be copied into controlled application storage rather than treated as trusted filesystem content.

---

# 8. Privileged Services

Any Shizuku-backed implementation must be kept as small as practical.

Preferred architecture:

```text
GameDeck UI
      ↓
Capability Interface
      ↓
Shizuku Adapter
      ↓
Minimal Privileged Service
```

The privileged service should not contain:

- Compose UI
- unnecessary business logic
- unrelated application functionality
- community-content parsing
- general configuration management

The privileged layer should perform only the privileged operation it exists to provide.

---

# 9. Shizuku Permissions

GameDeck must not assume that Shizuku always means root access.

The application should explicitly distinguish between:

```text
Shizuku unavailable
Shizuku stopped
Permission not granted
ADB / shell capability
Root capability
Unknown capability
```

Features should query capabilities instead of checking only:

> "Is Shizuku installed?"

A feature must fail closed when a required capability is unavailable.

---

# 10. Root Support

Root is not an initial product requirement.

Where root-assisted functionality is experimentally supported:

- it must be explicitly identified
- the user must understand the requirement
- the implementation should be isolated
- the normal application should continue to function without it where possible

GameDeck should never silently assume that a rooted device exists.

---

# 11. Input Security

The input subsystem is security-sensitive.

Potential privileged input operations should be:

- capability-gated
- session-bound
- scoped to the intended gaming session
- shut down when the session ends

When a gaming session terminates unexpectedly, active inputs must be released/reset.

Example:

```text
GameDeck process terminated
        ↓
Release active inputs
        ↓
Terminate privileged session
```

The system should avoid leaving an active privileged input channel running after GameDeck has stopped using it.

---

# 12. Session Isolation

A gaming session should have a unique session identifier.

Conceptually:

```text
Session ID
    ↓
Input backend
    ↓
Target application
```

Operations belonging to one session must not accidentally affect another.

This becomes particularly important if GameDeck eventually supports:

- multiple profiles
- rapid application switching
- streaming sessions
- background services

---

# 13. Foreground Application Safety

GameDeck may monitor which gaming application is currently active.

The application should verify that the target package is one of the applications associated with the active gaming session before performing session-specific operations.

Unexpected package transitions should result in safe behavior.

Example:

```text
Game session:
PPSSPP

Foreground:
Settings

→ suspend / safely disable session-specific input
```

rather than continuing to inject input blindly.

---

# 14. Overlay Security

GameDeck may use Android overlay capabilities for the controller interface.

The overlay implementation must:

- request only required permissions
- explain why the permission is needed
- avoid covering unrelated system UI unnecessarily
- stop when gaming mode ends
- clean up overlay windows correctly

An overlay should never be used to impersonate:

- Android system dialogs
- authentication screens
- banking applications
- password fields
- security prompts

The project's intended use is gaming.

---

# 15. Accessibility

Accessibility APIs must not be treated as a generic privilege-escalation mechanism.

If accessibility functionality is ever used as an input fallback:

- its exact purpose should be documented
- the user must knowingly enable it
- its behavior should remain limited to the intended gaming functionality
- the implementation must respect relevant Android platform and distribution requirements

GameDeck should not abuse accessibility APIs to perform unrelated automation.

---

# 16. Credentials and Secrets

GameDeck must never intentionally collect or store:

- passwords
- authentication tokens
- session cookies
- game account credentials
- banking information
- private keys

The project does not need access to users' gaming credentials simply to provide controller functionality.

---

# 17. Logging

Logs should contain useful diagnostic information without unnecessarily recording sensitive data.

Avoid logging:

- passwords
- tokens
- cookies
- private account identifiers
- screen contents
- private messages
- arbitrary user-entered secrets

Diagnostics should prefer technical identifiers such as:

```text
Android version
device model
GameDeck version
input backend
target package
session state
error type
```

---

# 18. Diagnostic Reports

Users may eventually be able to export diagnostic information for bug reports.

Diagnostic exports should clearly distinguish between:

```text
Technical information
```

and:

```text
Potentially sensitive information
```

The application should avoid including sensitive information by default.

---

# 19. Network Security

The core application should remain offline-first.

When GameDeck accesses a community repository:

- use HTTPS
- validate downloaded content
- verify expected file types
- enforce reasonable file-size limits
- handle network failures safely
- avoid executing downloaded content

Community network access should not require a GameDeck account.

---

# 20. Repository Trust

A GitHub repository is not automatically trustworthy just because it is hosted on GitHub.

GameDeck should use mechanisms such as:

- versioned manifests
- checksums
- schema validation
- compatibility metadata
- explicit repository configuration

Future implementations may consider signature verification.

---

# 21. Checksums

Community downloads should eventually support integrity verification.

Example concept:

```text
manifest
   ↓
expected SHA-256
   ↓
download
   ↓
calculate SHA-256
   ↓
compare
```

If verification fails:

```text
Do not import
```

---

# 22. Dependency Security

Third-party dependencies must be reviewed for:

- security history
- maintenance
- license
- Android compatibility
- unnecessary permissions
- transitive dependencies

Dependencies should be kept reasonably small.

A dependency should not be added simply because it saves a few lines of code.

---

# 23. Native Code

Native code creates additional security and maintenance risks.

GameDeck should prefer Kotlin/Java unless native code is technically necessary.

Any native component must receive additional review for:

- memory safety
- input validation
- buffer handling
- architecture support
- build reproducibility
- dependency provenance

---

# 24. Updates

The project should avoid building a custom update system unless there is a clear requirement.

An update mechanism that downloads and executes arbitrary code introduces significant security risk.

Any future update mechanism must be reviewed separately.

---

# 25. AI-Generated Code

Because AI-assisted development is part of this project, AI-generated code requires the same security review as human-written code.

AI coding systems may:

- invent APIs
- suggest insecure implementations
- copy patterns without understanding threat models
- omit permission checks
- mishandle input validation
- incorrectly assume privileged access

Therefore:

```text
AI-generated
    ≠
Trusted
```

Security-sensitive code should receive additional review.

---

# 26. Security-Sensitive Areas

The following areas should receive special scrutiny:

```text
Shizuku integration
Input backends
Root functionality
Overlay services
Accessibility
Community import
Network downloads
File extraction
Configuration parsing
Native libraries
Update mechanisms
```

---

# 27. Reporting a Vulnerability

Please do not immediately publish a serious security vulnerability as a public GitHub issue.

A private report is preferred when the vulnerability could:

- execute arbitrary code
- bypass privilege boundaries
- abuse Shizuku/root access
- expose user data
- compromise community content
- affect many users
- remain exploitable until fixed

---

# 28. Reporting Process

Until the project has a dedicated private security mailbox or GitHub security-advisory workflow, report serious vulnerabilities privately to the project maintainer through the project's configured private communication channel.

Do not include highly sensitive secrets in the initial report.

Provide enough information to reproduce the issue safely.

A useful report includes:

```text
Title
Impact
Affected version
Android version
Device
Reproduction steps
Proof of concept
Expected behavior
Observed behavior
Potential mitigation
```

---

# 29. Responsible Disclosure

The project asks security researchers and contributors to allow reasonable time for investigation and remediation before public disclosure of serious vulnerabilities.

The maintainer should make a good-faith effort to:

1. acknowledge the report
2. reproduce the issue
3. assess severity
4. develop a fix
5. test the fix
6. release the fix where practical
7. credit the researcher where appropriate and desired

---

# 30. Security Advisories

As the project matures, GameDeck should use GitHub Security Advisories or another appropriate private vulnerability-reporting mechanism.

A future security process may include:

- private reporting
- CVE assignment where appropriate
- severity classification
- patched-version information
- public advisory after remediation

---

# 31. Severity Philosophy

Not every bug is a security vulnerability.

Examples:

### Low

A crash caused by malformed local configuration with no privilege escalation.

### Moderate

A malicious community file can cause unexpected application behavior but does not escape its sandbox.

### High

A crafted community file can access data outside its intended scope.

### Critical

A remote or imported payload can gain privileged execution or abuse Shizuku/root capabilities.

Severity should be based on actual impact rather than how alarming the bug sounds.

---

# 32. Security Testing

Security testing should eventually include:

- malformed JSON
- oversized files
- invalid manifests
- corrupt images
- path traversal attempts
- unexpected package transitions
- Shizuku disconnects
- privilege changes
- service crashes
- lifecycle interruptions
- malicious community metadata
- checksum mismatches

---

# 33. Fuzzing

The project should consider fuzz testing for security-sensitive parsers, especially:

- JSON configuration
- manifests
- imported skins
- compatibility data

The goal is to discover crashes and unexpected states caused by malformed input.

---

# 34. Secure Defaults

GameDeck should prefer safe defaults.

Examples:

```text
Unknown community file
    ↓
Reject

Invalid configuration
    ↓
Do not load

Required privilege unavailable
    ↓
Disable feature

Unexpected session termination
    ↓
Release input

Checksum mismatch
    ↓
Reject download
```

The application should fail safely rather than attempting increasingly privileged workarounds.

---

# 35. No Security Through Obscurity

The project is open source.

Security should not depend on:

- hidden URLs
- undocumented JSON fields
- secret package names
- closed configuration formats
- pretending that privileged functionality does not exist

Security-sensitive designs should remain understandable and reviewable.

---

# 36. Security Documentation

Security-relevant architectural decisions should be documented in:

```text
docs/adr/
```

Security decisions use the same naming rule as every other record — sequential `ADR-NNN-topic.md`,
per `CONTRIBUTING.md` §57. There is no separate security numbering series.

Topics that would justify a record:

```text
community content trust boundary
Shizuku capability boundaries
input/session isolation
```

Check `docs/adr/` directly for the records that currently exist.

---

# 37. Contributor Responsibility

Contributors should report security concerns even if they believe the issue was caused by an earlier contribution.

Finding and reporting a vulnerability is a contribution to the project.

Contributors should not intentionally introduce:

- backdoors
- hidden telemetry
- credential collection
- malicious update mechanisms
- privileged functionality without documentation

---

# 38. Maintainer Responsibility

Maintainers should:

- take reports seriously
- avoid retaliation against security researchers
- avoid hiding confirmed vulnerabilities
- communicate remediation honestly
- avoid minimizing serious security problems
- prioritize user safety over protecting appearances

A vulnerability being embarrassing does not make it less important.

---

# 39. Transparency

GameDeck is intended to be an open-source project.

Security communication should therefore favor honesty.

If a vulnerability is confirmed, the project should not falsely claim:

> "This issue could never affect users."

Instead, describe:

- affected versions
- affected environments
- actual impact
- remediation
- recommended action

when disclosure is appropriate.

---

# 40. Scope

This security policy applies primarily to:

- GameDeck Android
- official GameDeck repositories
- official GameDeck configuration formats
- official GameDeck distribution mechanisms

Third-party applications such as:

- PPSSPP
- Dolphin
- RetroArch
- Moonlight
- Steam Link
- Xbox Cloud Gaming
- GeForce NOW

are outside GameDeck's direct security ownership.

Security issues in those applications should generally be reported to their respective maintainers unless GameDeck itself is responsible for the vulnerability.

---

# 41. Android Platform Vulnerabilities

GameDeck cannot fix Android platform vulnerabilities directly.

When an issue depends on:

- Android framework behavior
- OEM firmware
- kernel behavior
- vendor-specific services

the project should document the affected environment and, where appropriate, direct users to the relevant Android/OEM security updates.

---

# 42. Security and Project Scope

GameDeck is a gaming application.

Security controls should support the gaming experience without turning the project into an unnecessarily complex security platform.

The guiding principle is:

> Use appropriate security for the actual risk.

Do not add a massive infrastructure simply because a theoretical threat exists when the same risk can be reduced with a small, understandable control.

---

# 43. Final Principle

GameDeck is intentionally open source.

That means people should be able to inspect the code, understand how privileged functionality works, identify problems, and improve it.

The project's goal is not to claim that the software is perfect.

The goal is to make security problems:

- harder to introduce
- easier to detect
- easier to report
- easier to understand
- easier to fix

Security is part of maintaining trust with the people who use GameDeck.