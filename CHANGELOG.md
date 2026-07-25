# Changelog

## [0.1.1-beta] — 2026-07-25

- Fixed: the server crashed on shutdown. The config listener was subscribed to the `ModConfigEvent`
  base class, so it also received `Unloading`, which fires once values can no longer be read. Worlds
  and stored passwords were never at risk.
- Added: a mod icon for the mods list.

## [0.1.0-beta] — 2026-07-25

First beta. NeoForge 1.21.1, required on both client and server.

- Per-player passwords for offline-mode (`online-mode=false`) servers: the first login registers a
  password for that name, and later logins under it must prove the same one or are disconnected during
  the configuration phase, before the player is created in the world.
- One password per client, used for every server running the mod. Until it is set, the multiplayer
  button opens the password screen instead of the server list; afterwards the screen is reachable from
  the **Password** button in the list's top-right corner. *Save* continues to the server list, *Back*
  returns where the player came from.
- The password screen explains what the password does, that the first login registers it, and that
  only an operator can reset it afterwards.
- Challenge-response login, so the password never crosses the network: the client derives a 32-byte
  key with PBKDF2-HMAC-SHA256 (210,000 iterations, server-supplied salt), the server keeps it as a
  verifier and issues a fresh single-use nonce per login, and the client answers
  `HMAC-SHA256(key, nonce)`. A sniffed login cannot be replayed — which matters because an
  offline-mode server never enables packet encryption.
- Payloads registered as required, so a vanilla client without the mod is refused and the check cannot
  be bypassed by not installing it.
- `/serverpassword list` and `/serverpassword reset <name>` for operators (permission level 3);
  `reset` also disconnects the player if online. No command sets or reads a password.
- `allowNewRegistrations` config option to stop unknown names from claiming one.
- An unreadable `passwords.json` fails closed: every login is refused and the file is left alone,
  rather than starting empty and letting any name be re-registered.
- The host of a singleplayer or LAN world is exempt, so opening a world to LAN does not ask its owner
  for a password.
- English and Russian translations.
