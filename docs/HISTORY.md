# Commit history (auto-generated)

Every commit on main, oldest first. Lines +/- and files changed come from `git log --numstat`.
Merge commits (PR #1–#24) are left out; each PR merged the commits right above it.

| Commit | Date (UTC) | What changed | Area | Files | Lines + / - |
|---|---|---|---|---|---|
| `87653463` | 2026-09-30 16:42 | Add my folders | Android | 60 | +6516 / -0 |
| `88bb19a1` | 2026-09-30 11:20 | Add Gradle 8.11.1 wrapper scripts and jar | Android | 3 | +345 / -0 |
| `4da46819` | 2026-09-30 11:24 | Add APK build workflow, README, and dedupe online results | Android, CI, README.md | 3 | +75 / -1 |
| `14f70a31` | 2026-09-30 11:27 | Publish APK to a 'latest' GitHub release on main | CI, README.md | 2 | +20 / -1 |
| `4ba50668` | 2026-09-30 11:36 | Add Discover feed, auto playlists, suggestions, autoplay and CI playback test | Android, CI, README.md | 19 | +1111 / -9 |
| `363cd149` | 2026-09-30 13:28 | Add JioSaavn and YouTube sources, Indian categories, Resso-style For You feed | Android, CI, README.md | 29 | +849 / -22 |
| `287f13db` | 2026-09-30 13:33 | Parse Indian YouTube titles and sign builds with a fixed debug key | Android | 3 | +34 / -4 |
| `2e4ceb57` | 2026-09-30 13:37 | Smoother playback UI, online library, bundled YouTube key | Android, CI | 18 | +406 / -30 |
| `51706560` | 2026-09-30 13:38 | Log YouTube stream and deep link failures for diagnosis | Android, CI | 3 | +15 / -3 |
| `00e4e2ff` | 2026-09-30 16:27 | In-app updater and JioSaavn fallback for blocked YouTube streams | Android, CI | 11 | +374 / -5 |
| `6d4f58c0` | 2026-09-30 17:04 | Move YouTube API key out of the public repo into a GitHub secret | Android, CI | 5 | +9 / -8 |
| `400a09c0` | 2026-09-30 17:07 | Restore bundled YouTube API key as default (owner's choice) | Android | 1 | +2 / -0 |
| `0a4d4e37` | 2026-09-30 17:16 | Fades, listening log, Android Auto, headset resume, smart downloads, radio | Android | 12 | +407 / -13 |
| `e315d579` | 2026-09-30 17:24 | Widget, stats, artist pages, moods, swipe, search suggestions, strict language | Android | 29 | +1278 / -39 |
| `6107c040` | 2026-09-30 17:27 | Much larger recommendation pool and never auto-repeat seen songs | Android | 3 | +71 / -6 |
| `1b739310` | 2026-09-30 17:29 | Add AI DJ engine (Claude structured output with built-in fallback) | Android | 5 | +213 / -0 |
| `4d273fa5` | 2026-09-30 17:30 | Prefer YouTube Music; YouTube Mix radio as the main recommendation source | Android | 4 | +58 / -9 |
| `59b3e34f` | 2026-09-30 17:28 | Use standard English for all user-facing text | Android | 29 | +224 / -224 |
| `ce233ab1` | 2026-09-30 17:31 | AI DJ screen and a simpler UI | Android | 5 | +237 / -33 |
| `1c2ed694` | 2026-09-30 17:31 | Fix type inference for random playlist candidates | Android | 1 | +3 / -1 |
| `2b56fcb2` | 2026-10-01 06:47 | Add separate iPhone app (SwiftUI) and iOS CI build | CI, iOS app | 22 | +1731 / -0 |
| `76090ee3` | 2026-10-01 07:25 | Web app: API reachability check | CI, Web/iPhone | 3 | +68 / -0 |
| `11d8aa16` | 2026-10-01 07:26 | Web check: JSONP variants | CI | 1 | +12 / -0 |
| `9fa8a044` | 2026-10-01 07:28 | Web app: daily JioSaavn catalog builder and Pages workflow | CI, Web/iPhone | 5 | +217 / -10 |
| `4e258ffe` | 2026-10-01 07:35 | Add Sangeet web app for iPhone (installable PWA on GitHub Pages) | CI, Web/iPhone | 10 | +1203 / -6 |
| `89257e6f` | 2026-10-04 04:16 | Probe JioSaavn artist and album endpoints | CI | 2 | +42 / -1 |
| `51c9fc1d` | 2026-10-04 04:20 | Catalog: crawl every singer's songs, keep state between runs, search index; Android: no autoplay until first tap | Android, CI | 7 | +352 / -170 |
| `a7f50a1b` | 2026-10-04 04:24 | Lakhs-of-songs search and playlist import on web; Android: catalog songs in the feed, import Spotify/YouTube/JioSaavn links | Android, Web/iPhone | 9 | +467 / -25 |
| `8d2aabe3` | 2026-10-04 04:30 | Web workflow: print catalog summary at the end | CI | 1 | +8 / -3 |
| `aceaeec1` | 2026-10-04 04:34 | Web UI polish: artwork placeholder, double-tap like, browse moods and charts in search, swipe down to close | Web/iPhone | 2 | +90 / -5 |
| `29c8b60e` | 2026-10-04 04:47 | Catalog: deep crawl only Hindi, Punjabi, Haryanvi and English; longer crawl on main | CI | 3 | +7 / -20 |
| `a755d980` | 2026-10-04 10:27 | Android feed: Next/Previous start playback; emulator test checks the For You feed | Android, CI | 2 | +46 / -7 |
| `91c10c57` | 2026-10-04 13:06 | Android: feed never waits for the catalog or a slow source, catalog keeps 4000 songs per language in memory, songs start from the beginning | Android, CI | 5 | +123 / -76 |
| `f8d78d93` | 2026-10-04 13:06 | Emulator test: reopen the app with the catalog loaded and check memory | CI | 1 | +23 / -0 |
| `949f16f4` | 2026-10-04 13:22 | Smoother Android UI: recommendations off the main thread, quick screen fades, no full-screen blur in the feed, small thumbnails; offline-mode message in the feed | Android | 4 | +44 / -22 |
| `c8b85b0c` | 2026-10-04 13:23 | Android settings: switches flip instantly, a setting change no longer rebuilds the whole app | Android | 2 | +22 / -4 |
| `7da0996d` | 2026-10-04 13:26 | Ship the R8-optimized release APK instead of debug; keep rules for the Anthropic SDK; emulator test flags code removed by R8 | Android, CI | 3 | +25 / -6 |
| `dc19beea` | 2026-10-04 13:29 | R8: keep rules for the JSON schema generator used by the Anthropic SDK | Android | 1 | +5 / -0 |
| `39d85077` | 2026-10-04 14:22 | Android: duplicate-safe list keys (Home crash), baseline profile, emulator test opens every tab and measures startup | Android, CI | 15 | +75 / -12 |
| `d73c0129` | 2026-10-04 15:10 | Android: reliable downloads (JioSaavn first, 1 MB chunks, expedited), crash report, move library link, normalize volume, language mixes with distinct covers, lighter Home, speed dialog fix; daily health check; gh-pages publish | Android, CI | 16 | +512 / -50 |
| `edcb37fd` | 2026-10-04 15:12 | Web app: move library between phones (same link as Android), Made for you mixes | Web/iPhone | 1 | +110 / -0 |
| `6d8a9b49` | 2026-10-04 15:14 | Fix Home import; full UI walk test (every tab, button and option) on the emulator | Android, CI | 5 | +302 / -1 |
| `afd69543` | 2026-10-04 15:42 | UI walk: fresh start per section, tap the mini player itself | CI | 2 | +36 / -13 |
| `fada6920` | 2026-10-04 16:12 | UI walk: swipes sized to the emulator screen, tap a real search result | CI | 2 | +41 / -10 |
| `97c9ac4f` | 2026-10-04 16:40 | Now Playing: quality label shrinks so Speed/Sleep/Queue always fit on small screens; UI walk fixes | Android, CI | 3 | +24 / -13 |
| `308215fc` | 2026-10-05 02:18 | Download status screen: downloading with %, waiting, failed with retry, saved songs; progress notification | Android, CI | 11 | +279 / -7 |
| `9a04ae14` | 2026-10-05 02:24 | Setting: save downloads to phone storage (Music/Sangeet, stays after uninstall) | Android, CI | 6 | +109 / -0 |
| `a4906711` | 2026-10-05 02:25 | Never save a song twice: skip the same song from another source, in the app and in Music/Sangeet | Android | 3 | +66 / -11 |
| `0e58a011` | 2026-10-05 02:33 | Report a problem inside the app (GitHub issue via REPORT_TOKEN, or share); password lock on Music sources | Android, CI | 10 | +332 / -27 |
| `8520edd9` | 2026-10-05 02:40 | CI: say whether the in-app report token is set | CI | 1 | +1 / -0 |
| `f83f695d` | 2026-10-05 02:58 | UI walk: explain a failed download (worker log, current song) | CI | 2 | +9 / -3 |
| `a2560fef` | 2026-10-05 03:05 | Log why a download is skipped; UI walk downloads first, waits longer for speed | Android, CI | 3 | +10 / -6 |
| `da4f22c2` | 2026-10-05 03:31 | Fix: song menu and speed dialog actions were cancelled when the sheet closed (download, like, share, speed) | Android | 2 | +8 / -7 |
| `fe23e9b0` | 2026-10-05 07:29 | Web app: search by artist, fix misspelt singer names, show YouTube results | CI, Web/iPhone | 4 | +156 / -16 |
| `c8977b63` | 2026-10-05 07:32 | Web app: reliable lock screen / Control Center controls on iPhone | Web/iPhone | 1 | +40 / -13 |
| `1ad7cfd1` | 2026-10-05 08:51 | Search: a picked song is followed by similar songs, not other results | Android, Web/iPhone | 2 | +66 / -10 |
| `f17fecee` | 2026-10-05 08:55 | Android: Liquid Glass theme, and every page follows the chosen theme | Android, CI | 7 | +192 / -28 |
| `1d5721ae` | 2026-10-05 08:59 | New category: Haryanvi Badmashi (Android and web) | Android, Web/iPhone | 6 | +93 / -4 |
| `2083ffa0` | 2026-10-05 09:26 | UI walk: theme screenshots also go into the log; say what is on screen when a theme step can't find its button | CI | 1 | +19 / -5 |
| `8562e4f9` | 2026-10-05 10:00 | UI walk: log theme screenshots with Pillow (no ImageMagick on the runner); a screenshot problem never fails a step | CI | 1 | +15 / -3 |
| `ba7d3b02` | 2026-10-05 10:34 | UI walk: install Pillow before the walk (Ubuntu 24.04 needs --break-system-packages); close Now Playing before switching the theme back | CI | 2 | +8 / -3 |
| `71051d12` | 2026-10-05 11:06 | CI: copy emulator screenshots of the latest APK run to the ci-shots branch | CI | 1 | +36 / -0 |
| `b359e404` | 2026-10-05 11:31 | Web deploy: stop background git housekeeping in the gh-pages step (it made removing .git fail after a successful push) | CI | 1 | +4 / -1 |
| `9abee68e` | 2026-10-06 04:39 | Search: Spotify-style recent searches (Android and web) | Android, CI, Web/iPhone | 4 | +152 / -46 |
| `6aab8b0e` | 2026-10-06 04:45 | Liquid Glass redone the way Apple designs it (iOS 26) | Android | 25 | +405 / -140 |
| `1e4ed73d` | 2026-10-06 04:52 | Downloads: a broken piece is fetched again from where it stopped | Android | 1 | +34 / -5 |
| `a0219b1a` | 2026-10-06 10:55 | Voice search, search by a line of the lyrics, Liquid Glass light mode, status bar, tab taps | Android, CI, Web/iPhone | 7 | +253 / -20 |
| `61abcaee` | 2026-10-06 10:56 | Song languages outside Music sources; fix search build | Android, CI | 6 | +135 / -33 |
| `b43edbfe` | 2026-10-06 11:34 | Languages sheet opens fully; UI walk scrolls Settings to the top and shows the screen if the languages check fails | Android, CI | 2 | +18 / -7 |
| `704ec522` | 2026-10-06 12:56 | Web app: Liquid Glass theme (iOS 26 style) in Settings → Look | Web/iPhone | 3 | +94 / -0 |
| `da8d2166` | 2026-10-06 12:57 | CI: one-off check of the song CDN's CORS headers (for offline downloads in the web app) | CI | 1 | +24 / -0 |
| `9d6b6458` | 2026-10-06 13:01 | Web app: download songs to play without internet, and Save to Files | CI, Web/iPhone | 3 | +178 / -27 |
| `5a4472fd` | 2026-10-06 13:06 | Web app: the Android app's features on iPhone | Web/iPhone | 2 | +226 / -6 |
| `2bef3b50` | 2026-10-06 13:12 | Web app: Wrapped, Blend, lyrics card, Car mode, new songs from your singers | Web/iPhone | 2 | +252 / -5 |
| `ba87ecbf` | 2026-10-06 13:15 | Android: Wrapped, Blend, lyrics card, Car mode, new songs from your singers | Android, CI | 11 | +368 / -6 |
| `c42d3eeb` | 2026-10-06 13:25 | Fewer songs on YouTube: catalog grows 3x a day, smarter YouTube→catalog matching | CI, Web/iPhone | 2 | +79 / -21 |
| `be5267f8` | 2026-10-06 13:42 | Web: YouTube in background (experimental) via public Invidious/Piped servers | CI, Web/iPhone | 3 | +170 / -2 |
