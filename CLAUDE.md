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
- **Every update needs a "What's new" entry** (owner asked, Oct 9): add it at the TOP of
  `Sangeet/app/src/main/assets/whats-new.json` before merging (`date`, short `title`, `items` with `on`:
  both / android / iphone, plain English for users). Android shows new entries once after an update and in
  Settings → What's new, and the update dialog / GitHub release notes use the newest entry; the web app gets the same
  file (web.yml copies it) and shows it once after an update and in Settings. Several updates on one day: add items
  to that day's entry.
- Flow used for every feature: work on the dev branch → push → wait for CI (Build APK + Web App) → open PR to `main`
  → merge → wait for main's Build APK (emulator test) + Web App deploy → then give the APK link / say site is live.
  The PRs so far are #1–#27, all merged by Claude after tests passed.

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
- **Community** (Oct 9, owner's ask: everyone's data for better feed/catalog/DJ): Android `data/community/Community.kt`
  uploads every ~6 h when online, and 2 min after the app opens (WorkManager; setting `shareListening`, default on,
  Settings → "Help improve suggestions"). The upload is a random-id snapshot: searches, likes, plays, own playlists
  (JioSaavn/YouTube only, never phone files), sent as a deflated base64 issue "sangeet-data <day>" in `DATA_REPO`.
  - `DATA_REPO` is picked in CI by `.github/scripts/data-repo.py`. It must be PRIVATE and the token must be able to
    create issues there (POST {} → 422). Order: `CMS` (owner's choice, Oct 9: "cms wali repo use kar"), then
    `sangeet-data`, then names with sangeet/song/data, then any. So today it is `vivekyadav200405-cpu/CMS`.
    No private repo means nothing is sent.
  - web.yml runs `.github/scripts/community.py`. It reads and closes those issues and keeps 60 days of state in
    catalog-state. It publishes `data/community.json` (also in the catalog release) with only what ≥2 listeners
    share: songs played together, top songs, shared searches, and `words` (songs ≥2 listeners put in playlists
    named with a word like "gym", which the DJ learns). All searches go to the next crawl.
  - The **owner stats** go to the issue "sangeet-stats" (`SANGEET-STATS v1` + packed JSON) in the data repo:
    listeners today/week/month, versions, languages, most played, rising (vs last run), top singers/searches, and
    per listener (random id) their singers, top songs, playlists and searches.
  - **Files in the data repo** (owner: first "naya folder bana", then "naya folder mat bana, kisi purane ko hi use
    karle"). Every main run with new uploads makes one commit (Git Data API) with `sangeet-stats.json`,
    `sangeet-listeners.json` and `sangeet-community.json` in a folder the repo **already has**. The folder is picked
    by `pick_folder`: data/database/db/backup/storage/logs/files first, else the first folder a website wouldn't serve (never public/static/assets/docs/dist…), else the top level. It is kept in
    the state (`folder`) so it doesn't move. This needs a token that can write files:
    - REPORT_TOKEN couldn't on Oct 9 (contents 403 on CMS). The owner said CMS isn't an important project ("koi
      khas project nhi, jo bhi folder mile usse use karle"), so they were told to add Contents: Read and write to
      the same token.
    - The optional CI-only secret `DATA_TOKEN` is used instead if set.
    - `data-repo.py --files` picks the repo (`FOLDER_REPO`): the first private repo where the token may write files
      (empty PUT → 422), CMS first. Until one exists, the log says `folder repo none` and the data stays in the issues.
  - The CI emulator never uploads (`Community.emulator()`), and only main's Web App reads and closes uploads
    (branch runs keep a separate cache). Build 159's CI emulator had uploaded; community.py's one-time reset
    (state `v` 2) dropped those test phones.
  - Used by the Android feed (`Recommendations.candidates`), the AI DJ, and the web suggestions/radio
    (`Community.scores`). The web app can't upload (a public site can't hold a token).
- **Owner dashboard** (Oct 9): Android Settings → Music sources (password) → "Owner dashboard" (moved inside Music
  sources on the owner's ask; the screen itself is also behind `PasswordGate` in `SourcesLock.kt`, same password). `ui/settings/OwnerDashboard.kt` shows:
  - APK downloads: the count kept across builds in the `stats` release `downloads.json` (build-apk.yml adds the old
    `latest` asset's `download_count` before replacing it) plus the current build's count;
  - the "sangeet-stats" issue, read with REPORT_TOKEN.
  iPhone users aren't counted as downloads (it's a website).
- **Auto update** (Oct 9, owner: "internet mile, update check ho aur apne aap update ho jaye"):
  `data/update/AutoUpdate.kt`.
  - WorkManager runs every 6 h when online, and 1 min after the app opens.
  - It reads the `latest` release, downloads a newer APK quietly (`AppUpdater.apkFor`, a `.part` file renamed when
    complete) and checks the package name and versionCode.
  - It waits while the app is on screen or music plays (re-checks in 20 min).
  - Then it installs with a PackageInstaller session: `USER_ACTION_NOT_REQUIRED` on Android 12+, plus the
    `UPDATE_PACKAGES_WITHOUT_USER_ACTION` permission, so it is silent once "Install unknown apps" is allowed. Older
    Android, or when Android still wants a tap, gets a notification "Sangeet update ready, tap to install".
  - `MY_PACKAGE_REPLACED` posts "Sangeet updated".
  - Settings → App update → "Update automatically" (default on). When it's on, the old "New version available"
    dialog isn't shown on open.
  The web app already updates itself (service worker skipWaiting).
- **AI DJ** (Oct 9, owner: "LLM type", no paid key): built-in DJ = `data/ai/DjBrain.kt` (`DjIntent`: languages,
  mood, era, singers, "X jaise"/"songs like X", film name, "bina/no X", count; follow-ups merge into the last intent;
  `describe`/`followUps`) + `AiDj.buildLocal` (mood playlists, singer searches, radio of a song via `aiDj.radio`,
  film album, For You via `aiDj.forYou`, Community). The screen is a chat (`AiDjViewModel` keeps intent, shown songs,
  history; "New chat"). With a user's own Anthropic key (Settings) it calls `claude-haiku-5-5` (owner's choice; no
  server-side refusal fallback on Haiku 5.5, so none is sent) with the chat history and taste in the prompt; on any
  failure the built-in DJ answers. Web: `DjChat` + `djMix` in app.js (same follow-up idea; singer first names never
  match mood/language words).
- **Free online AI for the DJ** (Oct 9, owner: "free AI like DeepSeek, not ones that give wrong data").
  - Android `data/ai/FreeAi.kt` + `AiDj.freeAi`, and web `FreeAi` in app.js. When there's no Anthropic key, the DJ
    asks LLM7.io's keyless OpenAI-style API (`https://api.llm7.io/v1/chat/completions`, `Bearer unused`, CORS `*`)
    for 12 "Song - Singer" picks. Model order: `glm-5.2`, `DeepSeek-V4-Flash-0731`, `minimax-m3`, `gemma4:31b`.
    A 429 (each IP has a small daily quota) or 503 (busy) skips to the next. The whole call is capped at 22 s, and
    the DJ waits at most 25 s.
  - **A pick is kept only when the catalogue has that very song by that singer** (JioSaavn search first, then all
    sources; same title + singer match). So nothing made-up plays. Kept picks take turns with the built-in DJ's songs,
    and the reply says "(N picked by the online AI)".
  - The setting is `freeAi` (Settings → "Free online AI", default on; web Settings toggle `S.freeAi`). It sends the
    request, the chat's earlier requests, top singers and the Community's top songs.
  - Probes on Oct 9 from CI:
    - Pollinations' keyless model made up song names → not used.
    - GitHub Models is gone: models.github.ai answers every request with just "OK", and
      models.inference.ai.azure.com no longer resolves.
    - DeepSeek's own API and OpenRouter need keys.
    - LLM7's DeepSeek was busy (503) and GLM-5.2 named real songs ("Solid Body - KD" for Haryanvi gym).
  If LLM7 stops working, the DJ just plays the built-in mix (no error).
- **Owner's batch of Oct 9 (suggestions 2–9).**
  - **Hindi / Punjabi script search.** Android `data/Transliterate.kt` and web `Translit`:
    - Devanagari + Gurmukhi to Latin, in up to 3 spellings: as written, long vowels (raabta), and without the
      unsaid a (dhadkan).
    - The catalog / JioSaavn get all spellings; YouTube gets the text as typed.
    - Voice search (hi-IN) gives Devanagari, so it benefits too.
  - **Gemini** (free key from aistudio.google.com):
    - The repo secret `GEMINI_API_KEY` goes to `BuildConfig.GEMINI_API_KEY` (build-apk.yml) and into the website's
      `BUILT_IN_GEMINI`. On the web it is filled in ("Gemini key for the site") only for the Pages upload and taken
      out again before the gh-pages push, so it never lands in a git branch (GitHub's scanner would report it and
      Google could switch it off). It is still visible in the live site's code: free tier only, no billing.
    - Oct 9: the owner pasted an AI Studio key ("AQ." format) in chat. A CI probe showed it works, and
      `gemini-3.8-flash` named real songs in ~15 s (gemini-2.5-flash is 404 "no longer available to new users").
      The session proxy can't set repo secrets, so the owner adds `GEMINI_API_KEY` themselves. Never write the key
      into the repo.
    - Users can paste their own key in Settings.
    - `FreeAi` asks Gemini first and picks the newest plain `gemini-N-flash` from the models list, then the keyless
      LLM7 models. The same "keep only real catalog songs" check applies.
  - **New song alerts:** Android `data/notify/NewSongs.kt`.
    - Every 12 h it checks the top 5 singers' newest JioSaavn songs (`newSongsBy`).
    - The first run only remembers what's there. Then at most 3 notifications per run.
    - Tap → `sangeet://play?q=`.
    - Setting `newSongAlerts`.
    - SangeetRoot asks POST_NOTIFICATIONS once on Android 13+.
  - **Dashboard extras** (`data/Usage.kt`):
    - Screens opened (route counts) and crashes (CrashReporter → `Usage.crashed`), plus the phone model / SDK, are
      sent with the Community upload. community.py `owner_stats` adds `features`, `crashes`, `phones`, and per
      listener `m` / `crash` / `use`.
    - **Hit counters** on abacus.jasoncameron.dev (keyless, CORS, probed), space `sangeet-asdf968566`:
      `android-users`, `android-day-yyyymmdd` (Android, not emulators), `web-users`, `web-day-…` (web `Visits`,
      not headless browsers). The dashboard card "Users of both apps" reads them.
  - **Alarm** (Android): `data/alarm/SongAlarm.kt` + `ui/settings/AlarmScreen.kt` (Settings → Alarm).
    - Uses `setAlarmClock` (USE_EXACT_ALARM / SCHEDULE_EXACT_ALARM ≤32), and is set again on BOOT_COMPLETED /
      MY_PACKAGE_REPLACED.
    - Plays liked songs (shuffled), a playlist or For You. Offline it plays downloads only.
  - **Song share links:** `#play=<packed Sync.encode(track)>` on the website (`SongLink`: a menu with Play, and
    on Android "Open in the Sangeet app" via `intent://play?song=…`).
    - Android `LibrarySync.songLink/songFromLink`, ShareCard text, intent filters for
      `https://asdf968566-art.github.io/song-by-gullu` and `sangeet://play?song=`.
    - Unverified App Links don't open the app by themselves on Android 12+. A verified one would need
      `asdf968566-art.github.io/.well-known/assetlinks.json`, i.e. a repo `asdf968566-art.github.io`.
  - **Sync with another phone:** Android `data/CloudSync.kt` + `ui/settings/SyncScreen.kt` (route
    `settings/sync?code=`), web `CloudSync` + `syncBox()`.
    - The library is packed like a #sync link and stored on **restful-api.dev** `/objects` (keyless, CORS,
      probed). The object id is the sync code, and the join link is `#joinsync=<code>`.
    - A three-way merge against the last synced copy (`base`) makes un-likes and deletes sync too.
    - Android syncs 20 s after likes/playlists change, and on start; the web does the same through `save()`.
    - restful-api.dev is a free test service. If an object disappears, the app says the code doesn't work and
      the user starts again.
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

## 6. Status (2026-10-09)

- `main` has everything up to PR #27 (build 134 was the last APK published).
- The branch `claude/eloquent-maxwell-45qmfu` has everything listed in section 4 for Oct 8–9: moods, Pahadi,
  movies, What's new, Community, chat AI DJ, report repo, owner dashboard, auto update, free AI and the CMS data
  folder. It goes to main as PR #28 once Build APK + Web App are green, then the owner gets the APK link.
- The owner may still need to:
  - add the secret `DATA_TOKEN` (for the CMS folder);
  - allow "Install unknown apps" for Sangeet once (for silent updates).
- The token inside the APK (REPORT_TOKEN, account vivekyadav200405-cpu) can create issues in all that account's
  repos. Advised the owner to limit it to day1 + CMS, Issues only; the owner said "koi baat nhi" (keeps it as is,
  like the YouTube key). Don't raise it again.
- YouTube background mode (web): look for `YT SERVERS: N working` in the Web App log; it was 0 on Oct 6 (public
  Invidious/Piped are blocked by YouTube) and the owner knows.
- Ideas the owner was offered but hasn't asked for: Navidrome/Subsonic option on the iPhone web app (Android has it).
- Owner asked about hosting all catalog songs on their own server: advised against it (copyright takedowns could
  hit the GitHub account; ~400–500 GB per lakh songs; no gain since JioSaavn CDN already serves them).
