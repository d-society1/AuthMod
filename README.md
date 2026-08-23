# AuthMod — Player Login & Registration for Forge 1.20.1

Server-side authentication mod with `/register` and `/login`.

---

## Features

- `/register <password>` — register a new account
- `/login <password>` — log in
- `/changepassword <old> <new>` — change your own password
- `/authmod changepassword <player> <new>` — change a player's password (OP only)
- `/authmod resetpassword <player>` — remove a player's registration (OP only)
- Full lockdown until authenticated:
  - Spectator mode (previous game mode is restored after login)
  - Can't move, break/place blocks, interact with entities, drop/pickup items
  - All commands blocked except `/login` and `/register`
  - Chat disabled until authenticated
- Brute-force protection: temporary lockout after failed attempts
- Passwords hashed with PBKDF2-HMAC-SHA256 (120 000 iterations, random salt)
  - Old plaintext passwords are auto-migrated on first successful login
- Bilingual: **Russian** and **English** — language detected from the client automatically
- Server-side only — clients without the mod can join
- No OP required for `/register` and `/login`

---

## Installation

1. Download `authmod-1.5.0.jar` from [Releases](https://github.com/d-society1/AuthMod/releases)
2. Place it in the server's `mods/` folder
3. Set `spawn-protection=0` in `server.properties`
4. Start the server

> New player → sees `/register`
> Returning player → sees `/login`

---

## Configuration

On first run the mod creates `world/serverconfig/authmod-server.toml`:

```toml
[auth]
    minPasswordLength = 6        # Minimum password length (4–64)
    maxLoginAttempts = 3         # Failed attempts before lockout (1–100)
    lockoutSeconds = 60          # Lockout duration in seconds (1–3600)
    language = "auto"           # "auto" (client language), "ru", or "en"
```

Reload with `/reload` (requires Forge or a reload command mod).

---

## Data Storage

Passwords are stored in `authmod_data.json` in the server root directory.
Since v1.5.0 passwords are hashed with PBKDF2 — the file no longer contains plaintext.
Format: `"username": "pbkdf2:120000:<salt_hex>:<hash_hex>"`.

---

## Admin Commands (OP only)

| Command | Description |
|---|---|
| `/authmod changepassword <player> <newPassword>` | Set a new password for a player. Works with offline players. |
| `/authmod resetpassword <player>` | Delete a player's registration. If online, they are re-locked and must `/register` again. |

---

## Compatibility

- Minecraft 1.20.1
- Forge 47.4.0+
- Server-side only — clients without the mod can join

---

## Build from Source

1. Install **JDK 17** (any distribution, e.g. [Temurin](https://adoptium.net/))
2. Run the Gradle wrapper:
   ```sh
   ./gradlew build
   ```
   The wrapper downloads Gradle 8.1.1 automatically — no local Gradle install needed.
   The first build downloads Minecraft/Forge dependencies and may take 5–15 minutes.
3. The mod jar appears at `build/libs/authmod-1.5.0.jar`

---

## Upgrading from 1.0.0

- Passwords are auto-migrated from plaintext to PBKDF2 on first successful login
- The duplicate `mods.toml` in the project root has been removed
- Game mode is no longer forced to Survival — Spectator mode is used during auth, and the previous mode is restored after login
- Minimum password length is now configurable (default: 6, was hardcoded 4)

---

License: MIT
Author: dQryyx / d-society1
Releases: [GitHub](https://github.com/d-society1/AuthMod/releases)
