# Roadmap (phases 2–5)

Start only after the Pixel 3 XL checklist (PIXEL-TESTING.md) passes.

**Phase 2 – internet streaming (Milestone 6).** Feed the MediaProjection into WebRTC's `ScreenCapturerAndroid`
(hardware H.264/VP8, 1080p, 30 FPS target, bitrate from Quality, `degradationPreference = MAINTAIN_FRAMERATE`
or BALANCED). Signalling over WSS to a Node.js server, device auth with a per-device secret from pairing,
short-lived tokens, STUN + TURN (coturn, `turns:` on 443 for Jio networks that block UDP), ICE restart on
`NetworkMonitor` changes (Wi-Fi ↔ Jio), reconnect with backoff.

**Phase 3 – React viewer (Milestone 7).** Login, device list with online/offline, live player, full screen,
quality select, disconnect, `getStats()` panel. Chrome (Android/Windows) and Edge.

**Phase 4 – dashboard (Milestone 8).** Device registration and labels (Pixel 01…), grid of all screens, one large
tile, offline detection, search/filter, layout for the 85-inch display.

**Phase 5 – backend.** Node.js + PostgreSQL (devices, sessions), LiveKit SFU so 20 phones × several viewers don't
each need their own uplink per viewer, coturn, HTTPS/WSS, local Docker Compose first; a VPS only after the
single-device test passes.
