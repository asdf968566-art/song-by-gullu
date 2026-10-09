# Sangeet — project handoff for Claude

Read this first. It is the full story of the project so far (built 2026-09-30 → 2026-10-06 in one long Claude Code
session): what exists, why it was built that way, what broke and how it was fixed, the owner's rules, and what is
still open. `docs/HISTORY.md` has every commit with date, area and lines changed; `docs/PROMPTS.md` has every
request the owner made, in order.

## 1. The owner and how to work with them

- The owner writes **Hinglish** (Hindi in Latin letters, casual, typos). Reply in simple Hinglish, short.
  **App UI text is standard English** (owner asked: "english ui kar, hinglish nhi").
- They test on a real **Android phone** and an **iPhone 16**. They are not a developer: give links and taps, not code.
- **Standing rule — "test hone ke baad apk link de":** give the APK link only after the CI emulator test has passed
  on `main`. Don't push other app changes to main while a build is being tested ("tb tak koi aur update mat dena").
  APK link (always the same): https://github.com/asdf968566-art/song-by-gullu/releases/download/latest/Sangeet.apk
- iPhone app = website: https://asdf968566-art.github.io/song-by-gullu/ (Safari → Share → Add to Home Screen).
  Updates go live by themselves after the Web App workflow deploys; tell the owner when it is live.
- Owner wants: simple UI, no hints/clutter, lakhs of songs, no song repeating on its own, Hindi/Punjabi/Haryanvi first.
- Flow used for every feature: work on the dev branch → push → wait for CI (Build APK + Web App) → open PR to `main`
  → merge → wait for main's Build APK (emulator test) + Web App deploy → then give the APK link / say site is live.
  The PRs so far are #1–#24, all merged by Claude after tests passed.

## 2. Secrets — never put these in the repo, tests, commits or chat

- **Music sources password** (Android Settings → Music sources is locked): the owner gave it in chat. Only its
  PBKDF2 hash is in code (`ui/settings/SourcesLock.kt`, salt `sangeet-sources-v1`, 120000 iterations). Never write
  the password anywhere. Tests type a wrong password (1111) only.
- **GitHub token** (fine-grained PAT) for in-app "Report a problem": stored only as the repo secret `REPORT_TOKEN`
  (build-apk.yml passes it to `BuildConfig.REPORT_TOKEN`). Never commit or repeat it.
- **YouTube Data API key**: the owner chose to keep it in the public repo (`Sangeet/gradle.properties`,
  `sangeet.youtubeApiKey`) after being warned ("koi baat nhi"). Leave it, don't spread it further.
- Android signing: a fixed debug keystore `Sangeet/app/sangeet-debug.jks` is committed on purpose so every build
  installs over the last one (in-app updater needs the same signature).

## 3. What is in the repo

| Folder | What | Size (Oct 6) |
|---|---|---|
| `Sangeet/` | **Android app** (Kotlin, Jetpack Compose, Media3/ExoPlayer, Room, DataStore, WorkManager). 74 .kt files | 103 files, ~671 KB, ~14.1k lines |
| `SangeetWeb/` | **iPhone app as a PWA** (vanilla JS, no build step): `app.js` (~2.4k lines), `style.css`, `index.html`, `sw.js` | 9 files, ~283 KB |
| `SangeetiOS/` | Native SwiftUI iPhone app (Oct 1). **Abandoned** — needs sideloading (Sideloadly/AltStore, re-sign every 7 days), owner refused ("sideloadly wali mt kr"). Still builds an unsigned .ipa in CI. Don't develop it unless asked. | 21 files, ~66 KB |
| `.github/` | CI workflows + scripts (catalog crawler, emulator UI walk) | 12 files, ~142 KB |
| `docs/` | `HISTORY.md` (commits), `PROMPTS.md` (owner's requests) | |

### Android app (`Sangeet/app/src/main/java/com/sangeet/player/`)
- `data/remote/` — sources: **JioSaavn** (web API, api_version=4; media URLs DES-ECB decrypted with key "38346591"),
  **YouTube** via NewPipeExtractor (no key) + optional Data API v3, Audius, Jamendo, Subsonic/Navidrome.
  `OnlineRepository` orders sources and falls back YouTube → JioSaavn match (YouTube streams are bot-blocked on CI).
- `data/recommend/` — `Recommendations` (suggestions, radio, mixes; off main thread, 10 s deadline per source),
  `CatalogPool` (downloads the CI catalog from the `catalog` release, keeps 4000 songs/language in memory).
  Seen-store of 20k ids so nothing repeats on its own; strict language filter.
- `data/download/` — `Downloads` (1 MB Range pieces, each retried 3× from the same byte, expedited WorkManager,
  progress notification, JioSaavn first), `PhoneMusic` (optional save to Music/Sangeet, no duplicates).
- `data/` also: `Categories` (categories incl. "Haryanvi Badmashi", moods, festivals), `LibrarySync`
  (`#sync=` / `#blend=` links shared with the web app: deflate-raw + base64url), `CrashReporter`, `lyrics/`
  (LRCLIB + `LyricsSearch` via Genius for "search by a lyrics line"), `ai/AiDj` (Claude API, optional, with a
  built-in non-AI fallback), `update/AppUpdater` (reads the `latest` release "Sangeet build N").
- `playback/` — `PlaybackService` (MediaLibraryService, Android Auto), `EqualizerManager` (EQ, bass, normalize
  volume via DynamicsProcessing), `StreamResolver`.
- `ui/` — tabs Discover (Resso-style For You feed, nothing plays until first tap), Home, Search (recent searches,
  voice search hi-IN, lyrics-line search), Library (Downloads screen, Stats + "Share my Wrapped", Blend), Settings
  (themes, Song languages, Music sources (locked), Report a problem), Now Playing (lyrics, share-a-line card,
  Car mode, speed/sleep/queue), AI DJ, widget.
- `ui/theme/` — themes incl. **Liquid Glass** (`LiquidGlass.kt`: real backdrop blur via GraphicsLayer +
  RenderEffect, AGSL lens on API 33+; light + dark). Status bar icons follow the theme. Tapping a tab returns to
  that tab's start screen.

### Web app (`SangeetWeb/app.js`), the iPhone app
- Can't call JioSaavn from the browser (no CORS), so **CI crawls JioSaavn into static JSON** on gh-pages:
  `data/<lang>.json` (popular), `data/i/*.json` + `data/r/*.json` (search index over ~5 lakh+ songs),
  `data/artists.json`, `data/index.json`. Audio streams straight from `aac.saavncdn.com` (CORS `*`, ranges OK).
- Songs not in the catalog play through the **YouTube IFrame player** — these **stop when the iPhone is locked**
  (iOS rule). Mitigations: YouTube→catalog matching (`songKey`/`fitScore`, needs singer or film evidence; 38/64
  real results matched, 0 wrong), catalog crawl 3×/day, and the experimental **"YouTube in background"** setting
  (`YtAudio`: plays YouTube audio from public Invidious/Piped servers found by CI, `data/yt-servers.json`; falls
  back to the iframe when none works).
- Features parity with Android: artists search (typo-tolerant), recent searches, mic search, lyrics-line search,
  radio after a picked song, Liquid Glass look (Settings → Look), lock-screen controls (Media Session), downloads
  in IndexedDB + Save to Files, offline mode, repeat/shuffle/speed/sleep, stats + Wrapped card, Blend, lyrics card,
  Car mode, "New from your singers", festivals, accent colors, report a problem (share sheet), move library.
- Helper `h()` builds elements; use `fill()` instead of `replaceChildren` with arrays (arrays print as text).

## 4. CI (GitHub Actions)

- **Build APK** (`build-apk.yml`): build release (R8) → api-check → emulator-test (API 30;
  `.github/scripts/emulator-test.sh` plays Audius/JioSaavn/YouTube songs, checks downloads, memory, startup, then
  `ui-walk.py` taps every tab/button/option, ~88 steps, with screenshots) → on `main` publishes `Sangeet.apk` to
  release `latest`. Daily 02:41 UTC run opens an issue if it fails.
  It also builds **`Sangeet.aab`** (Android App Bundle for the Google Play Console) and puts it in the same
  `latest` release: https://github.com/asdf968566-art/song-by-gullu/releases/download/latest/Sangeet.aab
  The .aab is signed with a private upload key when the secrets `UPLOAD_KEYSTORE_B64` (base64 .jks),
  `UPLOAD_KEYSTORE_PASSWORD`, `UPLOAD_KEY_ALIAS`, `UPLOAD_KEY_PASSWORD` exist; otherwise with the repo key. The APK
  always keeps the repo key (so updates install over the old app). Play policy risk: YouTube playback/downloads
  (NewPipe) are likely to be rejected by Play review — told to the owner.
- **Web App** (`web.yml`): `build-web-catalog.py` crawls JioSaavn (state kept in actions/cache; 150 min on the
  3 daily crons 21:17/05:17/13:17 UTC, 10 min on push to main, 8 on branches) → `yt-servers.py` → publishes
  `catalog` release (gz files for Android) → pushes `gh-pages` → deploy Pages → check-site prints SITE LIVE.
- **CI screenshots** (`ci-shots.yml`): copies the latest emulator screenshots + `ui-walk.txt` to branch `ci-shots`
  (handy when artifacts can't be downloaded).
- **Build iOS** (`build-ios.yml`): unsigned .ipa for `SangeetiOS` (release `ios-latest`).

### Moods, categories, festivals (Oct 9)
- Never build these from a song search ("hindi happy songs" finds songs with the word in their name or singer, e.g.
  Happy Raikoti). Android: `OnlineRepository.topicTracks` / `moodTracks` take songs from JioSaavn **playlists** named
  for the mood (`search.getPlaylistResults`), drop songs whose singer has the mood word, and fall back to close moods
  (`Mood.close`). Web: `aiDj` uses catalog playlists named for the mood (`MOODS` 2nd list, 3rd = close moods); the
  crawler always keeps mood-named playlists (`MOOD_WORDS` in build-web-catalog.py).
- **Pahadi** (Garhwali/Kumaoni/Jaunsari/Himachali) is a language on Android (`LanguageGuess.isPahadi`, JioSaavn lists
  these songs as Hindi) and a category on both apps that mixes YouTube + JioSaavn searches (`Category.youtube`).
- NewPipeExtractor updated v0.24.8 → v0.26.5 (old one broke YouTube playback / playlist import). YouTube playlist
  import uses the Data API first (needs the key), NewPipe as fallback.
- **Movie / album search**: typing a film's name shows "💿 Movies & albums" cards (all its songs, Play plays them in
  order). Android: JioSaavn `search.getAlbumResults` + `content.getAlbumDetails`, route `online/album-<id>`
  (`ALBUM_PREFIX`). Web: `Albums.find` groups catalog songs by album name + year + language. Shown only when the
  album's name is what was typed (all typed words in it, or its whole name inside the typed words).
- **Movies** (Oct 9): `.github/scripts/build-movies.py` asks Wikidata (free, no key) for ~16,800 Indian films with
  year, languages, music directors, producers/studios, directors, top cast → `data/movies.json` (weekly, kept in
  catalog-state; also in the `catalog` release as movies.json.gz for Android). Web: `moviesPage` / `moviePage`
  (Search → 🎬 Movies, Library → Movies); Android: `ui/library/MoviesScreen.kt` (`MovieList`, `MoviesScreen`,
  `MovieScreen`, routes `movies?q=` and `movie?title=&year=&album=`), songs via `OnlineRepository.movieSongs`.
  Tap a name (music director, actor…) to see their other films.
- Other platforms checked Oct 9 from CI: Gaana's old open API is gone (404); Wynk, Hungama, Spotify have no open
  streams. JioSaavn + YouTube stay the sources.
- In-app "Report a problem" got 403 from GitHub. Cause (found Oct 9 from CI: `REPORT_TOKEN fingerprint/account`
  lines): the token belongs to the owner's **other** account, `vivekyadav200405-cpu`, and a fine-grained token can't
  create issues in another account's repo (even as a collaborator). The owner chose: send reports to a repo of
  the Vivek account. CI now picks the report repo itself (`REPORT_REPO`, BuildConfig.REPORT_REPO): this repo if
  the token can create issues here, else `vivekyadav200405-cpu/day1` (the owner's pick), else the first repo of the token's own account where it can (names with
  sangeet/song/report first). Log line: `REPORT_TOKEN: reports go to <repo> ✅` or a warning. If no repo works, the
  owner edits the token on the Vivek account: Repository access → that repo, Issues: Read and write. Until then
  the app opens Share instead. **Reports still end up here:** the `App reports` workflow (hourly,
  `.github/scripts/sync-reports.py`) copies open app reports (body has "**What happened:**") from the token's
  account into this repo (label "app report") and closes the original with a link. Read them here.

## 5. Problems hit and how they were solved (why the code looks like it does)

| Problem | Cause | Fix |
|---|---|---|
| First push 403 | Owner logged in as another GitHub account | Owner fixed login |
| YouTube songs fail in CI | "Sign in to confirm you're not a bot" | JioSaavn match as fallback |
| Owner can't sideload native iOS app | Free Apple ID, 7-day signing | Built the PWA instead |
| Website can't reach JioSaavn | No CORS / JSONP, public proxies dead | Catalog crawled in CI into static JSON |
| Android hang + crash on open, songs starting 1 min in | Feed waited for catalog; ~1 lakh songs in memory; hook preview started 30% in | Non-blocking pool (4000/lang), 10 s deadlines, hook preview off by default |
| UI jank | Recommendations on main thread, long nav fade, blur, big thumbnails | Moved off main thread, 140/90 ms fades, tiny-image background, 150 px images, optimistic switches |
| Home crash | Duplicate LazyList keys | Index-prefixed keys |
| R8 release build missing classes | Anthropic SDK / Jackson reflection | keep rules + `-dontwarn` |
| Download stuck at 0% | YouTube throttles one big GET; background work deferred | 1 MB pieces, JioSaavn first, expedited work |
| Download restarted from 0 silently | One broken piece failed the whole file | Each piece retried 3× from the same byte, logged |
| Menu actions (download/like/speed) sometimes did nothing | Sheet's coroutine scope cancelled on close | Use app scope `container.scope` |
| Liquid Glass light mode weak; status bar icons invisible | Tint/contrast; insets controller not following theme | Stronger shadow/hairline/tint; `darkTop` logic |
| Web search printed "[object HTMLDivElement]" | `replaceChildren(array)` | `fill()` helper |
| Web artist search picked wrong singer | Fuzzy match too loose | Weight by song count, same first letter, exact wins |
| YouTube→catalog wrong matches | Title-only matching | Needs singer/film evidence + same version (remix/lofi/…) |
| CI: ImageMagick missing / pip PEP 668 / gh-pages `.git` race | Runner image | Pillow + `--break-system-packages`; gc off + retry |
| Kotlin `if (...) async {} else null` won't compile | Type inference | Explicit `Deferred<…>?` type |
| Baseline profile | Gave no measurable gain on the emulator | Kept (harmless), reported honestly |

Sandbox notes for Claude Code on the web: the session proxy blocks jiosaavn.com, genius, lrclib, invidious/piped,
GitHub artifact/blob downloads and dl.google.com (no local Android SDK — build/test only in CI). Read job logs with
the GitHub MCP `get_job_logs` tool; test the web app locally with Playwright (Chromium is preinstalled) by serving
`SangeetWeb/` plus a copy of gh-pages `data/` and stubbing network routes.

## 6. Status at handoff (2026-10-06 ~13:50 UTC)

- `main` has everything up to PR #24 (web extras: Wrapped, Blend, lyrics card, Car mode, new songs). Site is live.
- On branch `claude/eloquent-maxwell-45qmfu`, **not merged yet** (CI was still running):
  - `ba87ecb` Android: Wrapped, Blend, lyrics card, Car mode, new songs from your singers
  - `c42d3ee` catalog crawl 3×/day + smarter YouTube→catalog matching (web)
  - `be5267f` web "YouTube in background (experimental)" + `yt-servers.py`
  - this handoff commit (CLAUDE.md, docs/, .gitignore)
- **Next steps:** check the Build APK and Web App runs for those commits (Actions tab). If green: PR to main →
  merge → wait for main's Build APK + Web App → give the owner the APK link and say the iPhone site updated. If
  red: read `ui-walk.txt` (ci-shots branch) / job logs and fix. In the Web App log, look for
  `YT SERVERS: N working` — if 0 for days, the YouTube background mode won't help; tell the owner.
  First run (2026-10-06 13:50 UTC): **0 working** (public Invidious/Piped are widely blocked by YouTube); the owner
  was told. The catalog was 11.8 lakh songs at that run (Hindi 7.4 lakh, Punjabi 2.1 lakh, Haryanvi 1.16 lakh).
- Ideas the owner was offered but hasn't asked for: Navidrome/Subsonic option on the iPhone web app (Android has it).
- Owner asked about hosting all catalog songs on their own server: advised against it (copyright takedowns could
  hit the GitHub account; ~400–500 GB per lakh songs; no gain since JioSaavn CDN already serves them).
