# Pathmate Maintenance Builds – How It Works

This document describes how to create a new Pathmate maintenance version of this Atmosphere fork and deploy it into the PMCP project.

## Background

Pathmate maintains a custom fork of Atmosphere 2.2.x. The version scheme follows this pattern:

```
2.2.13-pathmate-006
^^^^^^  ^^^^^^^^ ^^^
│       │        └── Pathmate maintenance increment (zero-padded)
│       └─────────── Pathmate identifier
└───────────────────── Upstream Atmosphere base version
```

The current version is always tracked in `version.properties`. This file must exist before running `set-version.sh`.

---

## Steps to Create a New Maintenance Version

### 1. Set the version

Edit `version.properties` to set the desired version:

```properties
version=2.2.13-pathmate-007
```

Then run:

```bash
./set-version.sh
```

This will:
- Read the version value from `version.properties`
- Update every `pom.xml` that contains a Pathmate version string with the new `version`

### 2. Build and deploy

Run the deploy script to compile and deploy the artifacts into the Maven repository:

```bash
./deploy.sh
```

This will:
- Call `set-version.sh` to apply the version from `version.properties`
- Run `mvn clean deploy` (skipping tests)
- Call `clear-version.sh` to reset all versions back to `000` for clean check-in

---

## Other Available Scripts

| Script | Purpose |
|---|---|
| `set-version.sh` | Apply the version from `version.properties` to all `pom.xml` files |
| `clear-version.sh` | Reset all Pathmate versions back to `000` for clean check-in |
| `deploy.sh` | Build and deploy artifacts into the Maven repository (calls set- and clear-version automatically) |

---

## Configuration

- **`version.properties`** – Tracks the current Pathmate version; must be updated manually before running `set-version.sh`

---

*Copyright © Pathmate Technologies AG – All Rights Reserved.*  
*contact@pathmate-technologies.com*
