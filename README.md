AuthMod — Player Login & Registration for Forge 1.20.1

The first server-side authentication mod for Forge 1.20.1 with `/register` and `/login`.

---

## Features
- /register <password> — register (min 4 characters)
- /login <password> — login
- Full lockdown until authenticated:
  - Can't move
  - Can't break/place blocks
  - Can't drop/pickup items
- Saves passwords in authmod_data.json
- No OP required
- Works with FTB Essentials (`/tpa`, /home, etc.)

---

## Installation
1. Download authmod-1.0.0.jar from [Releases](https://github.com/d-society1/AuthMod/releases)
2. Place it in the mods folder on the server
3. Set spawn-protection=0 in server.properties
4. Start the server

> New player → sees /register  
> Returning player → sees /login

---

## Compatibility
- Minecraft 1.20.1
- Forge 47.4.0+
- Server-side only — clients without the mod can join

---

License: MIT  
Author: d-society1  
Releases: JAR + source code  
Issues: Report bugs or suggest features!