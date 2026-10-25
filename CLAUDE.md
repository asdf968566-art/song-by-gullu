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
  both / android / iphone and `type`: `new` (feature) or `fix` (bug fixed)). Owner's rule: proper English, very short
  (a few words each, like "Automatic app updates"), only the main new features and real bug fixes; no emoji, no
  explanations, no Settings paths, nothing about data or where it goes. The apps and the release notes show "New" and
  "Fixed" separately. Android shows new entries once after an update and in
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
    complete) and checks the package name and versionCode (`AutoUpdate.isNewerSangeet`).
  - **Then it asks; it never installs by itself** (Oct 19: Play Protect showed the owner "Harmful app blocked" on an
    update. Silent self-install from the internet, waiting until the app isn't on screen, is what droppers do.)
    `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, the PackageInstaller session (`USER_ACTION_NOT_REQUIRED`) and its
    StatusReceiver are gone. A notification "Sangeet update ready, tap to install" (once per build,
    `ready_notified`) opens Android's installer (ACTION_VIEW + FileProvider), and on the next open the app shows the
    "Update ready" dialog once (`AppUpdater.readyApk`, `ready_shown`). REQUEST_INSTALL_PACKAGES stays for that tap.
  - `MY_PACKAGE_REPLACED` posts "Sangeet updated".
  - Settings → App update → "Update automatically" (default on): "New versions download by themselves; one tap
    installs them". When it's on, the old "New version available" dialog isn't shown on open.
  - If Play Protect still blocks it: "Install anyway" works; the developer can ask Google to look again with the
    Play Protect appeal form (the owner has to send it, signed in to Google).
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
- **Owner's batch of Oct 10** ("7 aur 9 ko chod kr saare kr de": all suggestions except Data saver and the
  verified App Links repo).
  - **Play Store version:** build type `play` (`initWith(release)`, `BuildConfig.PLAY_STORE = true`). CI runs
    `bundlePlay` → `Sangeet-play.aab` in the `latest` release
    (https://github.com/asdf968566-art/song-by-gullu/releases/download/latest/Sangeet-play.aab).
    - No YouTube: `YouTubeSource` is off, and Settings hides the YouTube group.
    - No self-update: `AutoUpdate` and `AppUpdater` do nothing, and Settings hides App update.
    - `src/play/AndroidManifest.xml` removes REQUEST_INSTALL_PACKAGES, UPDATE_PACKAGES_WITHOUT_USER_ACTION and the
      exact-alarm permissions. The alarm then uses an inexact alarm. CI fails if the bundle still asks to install
      packages.
    - The APK and `Sangeet.aab` are unchanged (full app).
  - **Live FM radio:** Android `data/remote/LiveRadio.kt` + `ui/library/RadioScreen.kt` (Library → Live radio,
    route `radio`); web `Radio` + `radioPage`.
    - Source: the Radio Browser directory (keyless, CORS `*`; `all/de1/de2.api.radio-browser.info`). CI probe on
      Oct 10 found ~300 Indian stations (Mirchi, Red FM, Vividh Bharati/AIR as HLS).
    - Only https streams are used: Android blocks cleartext, and the site is https.
    - Each station is a `SourceType.URL` track with sourceId `radio-<uuid>` (web `src: 'radio'`). A station isn't
      recorded as a play, can't be downloaded and has no lyrics.
    - HLS is flagged with `MimeTypes.APPLICATION_M3U8` (`MediaItems.EXTRA_HLS`).
  - **Automatic crash reports:** after a crash, MainActivity sends the report by itself (`CrashReporter.submit`,
    "(sent automatically after a crash)"). It asks only when that fails.
  - **Sing along:** Android `ui/nowplaying/SingAlong.kt` (mic button in Now Playing when the lyrics are synced);
    web `singAlong` (lyrics chip "Sing along"). It shows the current line very big, with the lines before and after.
  - **Lyrics meaning:** long-press a line on Android (`LyricLineDialog`: Meaning / Share as picture), or "Pick a
    line" on the web. `FreeAi.meaning` asks Gemini (text) first, then LLM7, with a 20 s budget; `<think>` blocks
    are removed.
  - **Song of the day:**
    - Android `data/notify/SongOfTheDay.kt`: a daily worker around 9:00, the first For You song not shown before,
      tap opens `sangeet://play?song=`. Setting `songOfTheDay`.
    - Web: a card at the top of Library (`songOfTheDay()`, `S.sotd`).
  - **Listen together:** Android `data/Together.kt` (`container.together`) + `ui/library/TogetherScreen.kt` (Library
    → Listen together, route `together?code=`); web `Together` + `togetherPage`. Both apps share one format.
    - The host makes a 6-letter code (no 0/O/1/I). The link is `#together=<code>`; Android also handles it and
      `sangeet://together?code=`.
    - Messages go through **ntfy.sh** (keyless, CORS `*`), topic `sangeet-tg-<code>`:
      - `{v,k:'s',from,o:<#play song JSON>,n,p:seconds,on}` from the host, on a change (song, play/pause, a seek
        > 3 s) and every 5 min;
      - `{k:'h'}` from a guest joining, which the host answers.
    - ntfy.sh allows ~250 messages a day per IP, so the host sends little.
    - Guests listen on `/<topic>/sse` (web) or `/<topic>/json` (Android, read timeout 100 s, reconnects). They
      play the host's song, seek when more than 2.5 s off, and copy play/pause.
    - Phone files and radio can't be shared; the guest sees a note.
    - Playwright test with a local ntfy stand-in: the guest stayed within 0.3 s, and pause, a new song and seeks
      followed.
- **Song videos** (Oct 10, owner: "song ka video bhi, jaise YT Music"): Now Playing has a **Song | Video** switch.
  - Video shows the song's YouTube music video in place of the cover, **muted and kept in step** with the song.
    The song itself keeps playing from the app's player, so the notification, lock screen and background play
    don't change.
  - Android: `YouTubeSource.videoFor` (a YouTube song's own video, else the YouTube Music video whose length is
    nearest the song's, within 20 s; a video-only progressive stream ≤ 720p, mp4 first; cached 1 h) and
    `ui/nowplaying/SongVideo.kt`. That file has `SongVideo`, `VideoPicture` (its own muted ExoPlayer on a
    TextureView; it copies play/pause and seeks back when more than 0.8 s off, at most every 3 s) and
    `SongVideoSwitch`.
  - Web: `SongVideo` in app.js plays a muted YouTube iframe (`Tube.search` for the id; needs `S.ytKey`). It syncs
    every second, and the setting `S.video` remembers the choice. A song that already plays through YouTube
    (`Player.mode === 'yt'`) shows its video as before.
  - Hidden for phone files, links, live radio and the Play version. With no video (or YouTube refusing), the app
    says "No video for this song" and goes back to Song. In CI YouTube usually refuses, so the UI walk only checks
    that the switch works.
- **Crystal theme** (Oct 10, owner: "ek new theme bna sb kuch transparent, saare tab saare button"):
  - Android `ThemeStyle.CRYSTAL`: surfaces are clear glass (white 8% / black 6%) with a hairline edge
    (`themedCard`). The play button is a clear circle with a white ring.
  - The page behind is the playing song's cover, blurred, under a veil (`CrystalBackdrop` in `ThemedBackground`;
    `Modifier.blur` on Android 12+). With nothing playing, the glass gradient is shown instead.
  - The tab bar and mini player are transparent. Filled Material buttons and chosen chips use clear glass
    (`colorScheme.primary` and `ThemeSpec.selectedFill`). Sheets and dialogs are 90% opaque so they stay
    readable.
  - Web: Theme → "Crystal · Dark / Light" (`body.crystal`, `html.crystal`, `#backdrop` with the cover, the
    `Crystal.update()` hook in `applyLook` / `trackChanged`).
  - The UI walk screenshots it (`crystal-*.png`).
- **Neon Glass theme** (Oct 13). The owner sent an Instagram reel (egpg.builds, "Never say: Build me a beautiful
  website") and asked for its "crystal glass" look: the "Liquid Glass" music app mockup (SoundWave).
  - Instagram can't be opened from the session. A one-off CI probe (yt-dlp) cut the reel into frames on the
    branch `probe-shots` (only reel pictures; safe to delete), and the colors were sampled from them.
  - Build 213 (PR #45) was v1: fixed blue-violet colors, accent hidden. The owner's feedback: "sbkuch kanch jaisa h
    … colour option bhi hta diya … same colour nhi, design waisa chahiye". So v2 (PR #46):
    - **Colors from the accent.** Android `NeonPalette.from(accent)` in Theme.kt (`ThemeSpec.neon`,
      `spec.neonColors`) and web `Neon.palette(hex)` in app.js (CSS vars `--n-*` on `<html>`) use the same numbers:
      - the page runs from hue −35° to hue +15°, darkened (`fit`) until white text stays readable;
      - glows at −60/+25/+50/+80°, the fill gradient at +25° → −5°, and the "on" color (hearts, seek bar) at +65°.
      - Violet gives the reel's blue → violet page with pink hearts.
      - The accent picker is back for this theme. It is always dark.
    - **Everything on glass**, like the reel. `neonGlass` = see-through gradient, a shine on the upper half, and a
      bright top-left edge. `neonCircle` = a round glass button. `neonFill` = the glowing gradient.
      - Song rows (`NeonTrackRow` in TrackViews.kt; web `trackRow` when `body.neon`) are glass cards with a heart
        and a round glass play button. The heart is hidden for radio and on phones under 340 dp.
      - Shelf / mix / playlist cards are glass with a play button on the cover (web `coverBox`). Quick tiles are
        rounder.
      - Home has `NeonHero`, the reel's "Good Vibes Only" card, for the first mix. "Recommended for you" is
        5 glass rows.
      - On the web, Library's Song of the day becomes that big card ("Play now").
      - Search's category tiles are glass with the category color glowing from a corner (web `--c`).
      - Now Playing: sound bars beside the cover move while playing (`NeonBars`; web `.np-body::before/::after`
        with `body.is-playing`). There is a glass heart on the cover (Android), round glass tool buttons, and an
        "Up Next" glass card (tap: next song; See all: the queue).
      - Tab bar: a floating glass bar, the chosen tab on a glowing tile (`NeonTabBar`). The mini player is a glass
        card. Settings groups and library tiles use the same glass.
  - Web: the tab bar and mini player are nearly opaque (`93%`), so text under them can't be read. Rows have no blur,
    so long lists stay smooth.
  - UI walk: `neon-*.png` screenshots (ci-shots `theme-neon-*.png`).
- **More Haryanvi and Pahadi songs** (Oct 13, owner: "pahadi gaane aur add kr aur haryanvi bhi aur add kr").
  - Crawler (`build-web-catalog.py`):
    - Haryanvi gets more room: `DEEP` 0.3 → 0.6, `CORE_CAP` 15k → 30k and 900 playlists. `HARYANVI_TERMS` adds
      playlist searches like "haryanvi dj" / "ragni" / "folk", 2 pages each.
    - `SEED_SINGERS`: Haryanvi and Pahadi singers are found with an artist search once a week (`seeded` in the
      state) and put first in the deep crawl (`seen` 40).
    - `pahadi()`: playlist searches for `PAHADI_TERMS`. Only playlists whose own names say Pahadi are kept, and all
      their songs are trusted; song searches keep only songs whose names or singers match `PAHADI`. The ids are kept
      in the state (`pahadi`).
    - `write` makes **`data/pahadi.json`** (same format as a language file, up to 20k songs): the trusted songs plus
      every catalog song matching `PAHADI`. It is also `pahadi.json.gz` in the `catalog` release.
    - Log lines: `pahadi: N playlists found…`, `seed singers: …`, `pahadi: N songs in catalog…`.
  - Android:
    - `CatalogPool.songs(lang)` and `AppContainer.catalog` / `catalogFor(cat)`: the catalog's songs that fit a
      category (its `match` on name+singer+album for YouTube-style categories, on the title otherwise) are mixed into
      `categoryTracks` / `youtubeCategory` (`extra`). Home rows use 2 YouTube searches + the catalog.
    - Users with the Pahadi language get pahadi.json in the feed too.
    - New categories: "Haryanvi" now has singer searches (`more`), plus "Haryanvi DJ" (`HARYANVI_DJ`) and
      "Haryanvi Folk" (`HARYANVI_FOLK`). Pahadi has 8 searches, plus "Garhwali" / "Kumaoni" / "Himachali"
      (`GARHWALI`, `KUMAONI`, `HIMACHALI`).
    - `LanguageGuess` PAHADI has more singers.
  - Web: 'pahadi' is in `ALL_LANGS` (a song language on the iPhone too). The same new `CATEGORIES` exist; Pahadi ones
    read `pahadi.json` (`lang: 'pahadi'`, `all: true` for Pahadi itself) and fall back to the Hindi catalog filtered
    by name (`alt: 'hindi'`) until that file exists.
- **Phone details in reports** (Oct 10, owner: "kisi specific phone me problem aaye to us problem ke liye data le
  sakta hai, koi sensitive mt lena"): `data/Diagnostics.kt`, added to every report by `CrashReporter.build`.
  - It sends: maker / model / Android / ROM build, screen dp and density, font scale, the phone's dark mode,
    status bar / cutout / navigation bar sizes, the navigation mode, and the status bar icon colour the app set
    per screen.
  - It also sends the theme / dark mode / accent, offline mode and YouTube on or off, online or not, which source
    is playing, and the last 6 screen route patterns.
  - It never sends songs, playlists, searches, names, accounts, files or location. Reports go to day1, which is
    public.
  - SangeetRoot fills in the bar sizes and icon colours.
- **Owner's app reports of Oct 10** (build 174, realme Android 16; issues #32–#34, copied from day1):
  - "aidj nhi chal rha" / "ai ko dubara check kr" (log: `Gemini: timeout`): Gemini was asked first and alone, its
    free tier used the whole 22 s, so LLM7 was never tried and the DJ showed nothing for 25 s.
    - CI probe (Oct 10, real key): `gemini-3.8-flash` answers in ~19 s with or without thinking
      (`thinkingBudget: 0` gives no thoughts but is not faster). `thinkingLevel: "minimal"` is a 400 on these
      models, and 503 "high demand" is common. So thinking settings are not used.
    - Fix: `FreeAi.race` on both apps (Android: cancellable OkHttp calls; web: AbortController) asks Gemini and
      the keyless models at the same time; the first useful answer wins and the others are cancelled. This is
      used for the DJ and for lyrics meanings (25 s).
    - The DJ plays the built-in mix at once (`AiDj.make(waitForFreeAi = false)`). The online AI's real songs come
      later (`AiDj.freePicks`, `AiDjViewModel.mixInAi`; web `mix.later` in `djPage`) and go between the songs still
      to come (`PlayerConnection.mixIntoQueue`; web `Player.queue.splice`), only while that mix still plays. The
      reply then gets "(N picked by the online AI.)".
  - "home me refresh button kaam nhi kr rha": tapping refresh showed nothing and brought the same shelves. Now the
    icon spins until every shelf has loaded (`Ui.refreshing`). Each tap brings other suggestions (the ones on screen
    are excluded), the next featured-playlist page, the next categories and shuffled trending.
  - Issue #35 (LAVA LXX525, Android 16), four asks:
    - "theme ka effect har page pe aur tiles par bhi": Library tiles take the theme's card style (`themedCard`)
      and are tinted to its accent (`lerp` with `spec.accent`); shelf covers (`ShelfCard`) use `themedCard` too.
    - "for you page par time, network, battery sahi se show nahi" (again in #38, build 184). The real cause: every
      page starts below the status bar (Scaffold padding on the NavHost), so the theme's background shows behind the
      time and battery. For You sets light icons because the page is black, so on a light theme they were light
      on light. Build 184's band inside For You sat below the status bar and didn't help. Now SangeetRoot draws a
      black strip behind the status bar while For You is shown (`windowInsetsTopHeight(WindowInsets.statusBars)`
      in ThemedBackground, under the Scaffold). It also sets the icon colors again on every ON_RESUME. The UI
      walk screenshots For You in Liquid Glass light (`light-ForYou.png` on ci-shots). CI used dark themes, so it
      never showed this.
    - "library me user ki khud ki playlist top me": Liked Songs and your playlists come first, on both apps.
    - "playlist se hamesha first song": Play on playlists, mixes and lists (`CollectionHeader(playShuffled = true)`;
      web `songsPage`) starts at a random song, shuffled. Shuffle used to start at the first song; now it is random
      too. Albums, films and artists still play in order.
  - Issue #38 (same LAVA tester, build 184):
    - The status bar again (see above).
    - "add to playlist besides like": a PlaylistAdd button next to Like on For You (the six controls are 40 dp
      with no gaps, to fit 320 dp) and in Now Playing. On the web it is a plus button in Now Playing.
    - Library order and "thumbnail instead of list": the Playlists tab is a two-column tile grid
      (`LazyVerticalGrid`, `LibraryTile`). Order: Liked Songs and your own playlists, then Online Library,
      Movies, Your Stats, Downloads, Blend, On this phone, then the auto playlists (names starting with ✨),
      Recently played, Live radio and Listen together. Albums and Artists are tiles too. The web keeps its list in
      the same order.
  - Web Settings redesign (owner: "setting ka ui shi se bna"): grouped cards like the iPhone's Settings
    (`setGroup` / `setRow` / `setSwitch`, coloured icon tiles, values with chevrons).
    - Languages and Sync have their own pages (`languagesPage`, `syncPage`, `syncBox` in the same style).
    - Theme and quality use `menu()`; keys use `editSheet`. The long notes are gone.
  - The `App reports` cron ("23 * * * *") ran only a few times a day. Run it by hand (workflow_dispatch) to read new
    reports.
- **Owner's batch of Oct 11** ("Missing gaane catalog mein aur Sangeet Top 50 kr, bs ye bhi Ringtone"):
  - **Missing songs into the catalog** (so fewer iPhone songs need YouTube, which stops when locked):
    - Web `Share` (app.js): every 6 h (25 s after opening, hourly check, and when the page is hidden) one message of
      ≤ 3.8 KB to **ntfy.sh topic `sangeet-web-listens-k4q8`** (keyless, CORS `*`; ntfy keeps it 12 h): random id,
      `p: 'web'`, build, `m` (iPhone/iPad/browser), languages, `week` (catalog ids played in 7 days), `plays`
      ([id, count], recent first), `likes`, `searches` (words that found < 8 songs, not 4+-word lyrics lines;
      `Share.search` in searchPage's YouTube step) and `miss` ([title, singer] of songs that played from YouTube with
      no catalog match; `Share.miss` in `Player.load`). Setting `S.share` (Settings → Help → "Help improve
      suggestions", default on). Headless browsers don't send unless `Share.send(true)`.
    - community.py `fetch_web` reads `<topic>/json?poll=1&since=12h` on every run (message ids kept 2 days so each
      counts once), looks the ids up in the catalog state (`argv[4]` = catalog-state/state.json.gz) and stores the
      phone as listener `web-<id>` (searches / misses merged with its earlier ones). So iPhone listeners now count in
      the owner stats and Community too. Crons are 8 h apart, inside ntfy's 12 h.
    - The searches file now also has `wanted_query()` of every YouTube song Android listeners play and the web
      misses ("title singer", label-like channel names dropped). build-web-catalog.py looks up to 800 of them,
      each at most every 3 days (`searched` in the crawl state). Log: `community searches: N looked up, +M songs`.
  - **Sangeet Top 50** (both apps): community.py `chart` = songs the most listeners played in the last 7 days
    (≥ 2 listeners; Android uploads now send `week`: "SOURCE:id" of songs played in 7 days; older builds count their
    latest 60 plays), then `top`. The apps keep the user's languages and, while there are few listeners, fill up to
    50 with trending (Android `c.online.saavn.trending`; web the catalog's chart playlists).
    - Android: `data/community/Top50.kt`, `Community.await()` / `Data.charts()`, Home shelf "🏆 Sangeet Top 50"
      (after Recommended) → See all = `ListKind.TOP50` (numbered rows, subtitle "This week's most played").
    - Web: `top50()` / `top50Page()` (`.ranked` rows), first tile of Search → Browse all.
  - **Set as ringtone** (Android only; a website can't set an iPhone ringtone): song menu → "Set as ringtone"
    (`ui/components/RingtoneSheet.kt`, `data/ringtone/Ringtone.kt`).
    - The song comes from its download, the phone file, or is fetched into cache/ringtone in 1 MB pieces.
    - The part (30 s) starts 0.5 s before the chorus (the synced lyrics line sung most often, ≥ 8 letters, first
      time after 15 s), else 30% in; a slider moves it, "Listen" plays it on its own ExoPlayer (pauses the song).
    - Cut without re-encoding (MediaExtractor + MediaMuxer): AAC → .m4a, Opus/Vorbis → .webm, MP3 frames → .mp3.
      Saved in Ringtones/Sangeet (IS_RINGTONE/ALARM/NOTIFICATION; Android 8–9 use the file path and ask
      WRITE_EXTERNAL_STORAGE, maxSdk 28).
    - Ringtone / Alarm / Notification chips; setting needs WRITE_SETTINGS ("Modify system settings": the sheet shows
      Allow → `ACTION_MANAGE_WRITE_SETTINGS`, and sets it on return).
    - The UI walk runs `appops set com.sangeet.player WRITE_SETTINGS allow` and checks `settings get system ringtone`
      plus /sdcard/Ringtones/Sangeet.
- **App report #41 of Oct 12** (LAVA LXX525 tester, build 199): "network slow ho jaaye ya down ho jaye to instant
  next song pe switch na ho, 30 sec network ka wait karna hai", and "pause/play jhatke se naa ho, volume dheere
  dheere". Android only (iPhone Safari ignores an audio element's volume, so the web can't fade).
  - `PlaybackService.isNetworkProblem` (no internet, a connection failure / timeout, or a socket / DNS / TLS error in
    the cause; never in offline mode, and a bad link still skips) → `waitForInternet`: the song stays where it stopped
    and is prepared again whenever `network.status` is online, for 30 s from the first error (`netLostAt`, reset when
    something plays or the song changes). After 30 s a downloaded song / phone file further on plays; with none, the
    song waits and goes on when the internet is back (within 10 min). Log lines start with "internet problem" /
    "no internet for 30 s".
  - Fewer errors to begin with: `playback/NetworkRetryPolicy.kt` retries a load up to 10 times (1–3 s apart) only
    when the connection is the problem; anything else (a refused YouTube link, an HTTP 4xx) gives up after 3 tries as
    before. (The first try, `DefaultLoadErrorHandlingPolicy(8)` for all errors, made CI's blocked YouTube songs take
    25 s+ to skip and failed "For You: Play".) The buffer keeps up to 3 minutes ahead by time
    (`setBufferDurationsMs(50_000, 180_000, 1_000, 2_000)`, `setPrioritizeTimeOverSizeThresholds`).
  - `playback/FadingPlayer.kt`: a ForwardingPlayer given to the media session (app, notification, lock screen,
    headset buttons). Pause fades out over 450 ms and then pauses; play starts silent and fades in over 800 ms; a
    tap during a fade turns it around. The app's own volume (crossfade, sleep timer) is multiplied in
    (`appVolume × level`). Unplugged headphones / calls still pause at once (ExoPlayer's own path).
  - UI walk step "No internet during a song: waits on it, then plays": `svc wifi/data disable`, media Next, the title
    must stay the same for 13 s, then `enable` and it must play that song.
- **App report #43 of Oct 12** (Xiaomi 23124RN87I, Android 15, build 174): "adhe song nhi chal rhe respond dikha rha
  hai". The mini player showed the raw error "Response code: 403" (an HTTP 4xx from the stream link, most likely
  YouTube's). Now `PlaybackService.isRefusedLink` (ERROR_CODE_IO_BAD_HTTP_STATUS / InvalidResponseCodeException) →
  `OnlineRepository.refuse(track)` once per song and `prepare()` again: a refused YouTube song plays its JioSaavn match
  (else a fresh YouTube link, `YouTubeSource.forget`), a refused JioSaavn song plays at 96 kbps. A second refusal
  skips as before. The error line now reads "This song can't play right now" / "Waiting for the internet…"
  (`PlayerConnection.errorText`). CI can't reproduce it (YouTube is bot-blocked there before any stream).
- **App report #48 of Oct 14** (LAVA LXX525 tester, build 221): "app open karte hi jab playlist se song play karte
  hai toh you are offline ka error … jabki internet connection hota hai", and "100% buffering honi chahiye taki
  connection lost ho toh song ruke nahi". Android only.
  - Cause: when the app process starts in the background (an update check, Song of the day…), Android can block its
    network, and `activeNetwork` is then null. `NetworkMonitor` didn't handle `onBlockedStatusChanged`, so the status
    stayed "offline" after the app came on screen, and `StreamResolver` threw "You're offline or offline mode is on"
    (the mini player showed that raw text).
  - Fix: `NetworkMonitor` handles `onBlockedStatusChanged`, falls back to `allNetworks`, and has `refresh()` (asks the
    phone now). SangeetRoot calls it on every ON_RESUME. `StreamResolver` asks again for up to 4 s
    (`OnlineRepository.waitForInternet`) and then throws `DataSourceException(ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)`,
    so `NetworkRetryPolicy` retries it and PlaybackService's 30 s internet wait takes over (it uses `refresh()` too)
    instead of skipping. Offline mode still says "Offline mode is on".
  - Buffer: `setBufferDurationsMs(600_000, 900_000, 1_000, 2_000)` with time over size, i.e. the whole song (and the
    start of the next) loads ahead. A 5-minute song at 320 kbps is ~12 MB of memory.
- **App report #51 of Oct 17** (LAVA LXX525 tester, build 225, Liquid Glass light, Teal): "jo neon glass theme ka
  effect hai … same liquid glass theme ke liye bhi kar de", For You "alag tareeke se dikhta hai", and "jo song play ho
  uske jaise song chalne chahiye … 90s ka koi song chalaya toh 90s ke usi genre ke". Android only.
  - **Liquid Glass on every page**, like Neon Glass. Surfaces.kt: `ThemeSpec.glassEverywhere` (NEON or LIQUID_GLASS),
    `glassPanel` / `glassCircle` / `glassInk` (Neon's `neonGlass` / `neonCircle` / white, or Liquid Glass's
    `liquidPane`: a frosted cool-white / graphite pane with a sheen and the bright rim, a hairline in light mode),
    `liquidBackdrop` (soft accent glows under the page so the glass shows something). `themedCard` on Liquid Glass is
    now `liquidPane`. Every Neon layout follows `glassEverywhere`: glass song rows (`GlassTrackRow`), shelf cards,
    the Home hero (`NeonHero`, Liquid fades into its own pane and uses its play button), 5 glass rows for
    Recommended, Search's category tiles, Library tiles (18 dp), and Now Playing's glass heart, tools, Up Next card,
    sound bars and cover rim. The floating tab bar / mini player keep their real-blur `liquidGlass`.
  - **For You takes the theme** (`feedSpec()`: the theme with dark glass, since the page is always dark over the
    cover). The play button is the theme's own (`playButtonStyle`; plain accent on Neumorphism), glass themes get
    round glass controls (`feedTool`), a glass rim on the cover, colored chips and seek bar, and Neon its page tint.
  - **For You follows the song** (`DiscoverViewModel.follow`): a feed song that plays 25 s (or is liked) gets up to
    6 songs like it right after it, in the feed and in the queue (`PlayerConnection.insertAfter`). They come from
    `RecommendationRepository.likeThis`: the singer's own songs (search, a deeper JioSaavn page, the phone, the
    catalog) taking turns with JioSaavn's `reco.getreco` and YouTube Music's similar list; same years first
    (`Track.year`, new: from JioSaavn's `year` and the catalog row; ±4 years +2, >14 years −2.5), its language;
    music-label channel names (`LABELS`) are not singers. Moods don't follow. Radio (`rank`) also prefers the same
    years. Log lines "For You: N songs like '…' come next"; the UI walk checks it after "For You: Play".
- **App report #53 of Oct 17** (same tester, build 231, Liquid Glass dark): "classic dark, amoled black and material
  you theme ko remove kar de", glass buttons overlapping (Liquid and Neon), For You's add-to-playlist "song name ke
  aage", and the player's play / pause / next / previous "solid colour … theme ke sath match kar". Android only.
  - **Themes:** `ThemeStyle.removed` (SPOTIFY = Classic Dark, AMOLED, MATERIAL_YOU) stay in the enum so old settings
    read, but the picker shows `ThemeStyle.choices`, and a stored removed theme reads as LIQUID_GLASS. The default is
    now LIQUID_GLASS (dark mode is DARK by default), so the CI walk runs in Liquid Glass; its Now Playing Play / Pause
    steps tap "Play/Pause" first (the glass song rows under Now Playing have "Play" buttons), and the last theme step
    checks that Classic Dark is gone, then goes back to Liquid Glass.
  - **Overlap:** an `IconButton` grows to its 48 dp touch size, so a glass circle put on its modifier came out bigger
    than its 40 dp slot. Glass buttons are now `GlassIconButton` (TrackViews.kt: a Box with `glassCircle`). Used on
    For You (`FeedButton`, gaps between them), the For You top bar, and Now Playing's tools.
  - For You: Add to playlist is a 36 dp button right after the song's name. The control row is Like, Previous, Play,
    Next, More.
  - Liquid Glass's play button (`playButtonStyle`) is see-through accent glass with a colored glow, the sheen and
    the rim (white icon unless the accent is very light). On glass themes, Now Playing's Previous / Next are 54 dp
    glass buttons.
- **App report #55 of Oct 17** (same tester, build 236, Liquid Glass dark): no Up Next card in Now Playing, "sirf lyrics
  … sahi se visible" (they had to scroll), a stylish progress bar that shows what has loaded, Sort by in own
  playlists, no song twice in a playlist ("version ya singer alag ho toh ho jaaye"), long-press drag in the queue,
  and no animation beside the cover. Android only.
  - **Now Playing:** the Up Next card and the sound bars (`NeonBars`) are gone on every theme. In a `BoxWithConstraints`,
    `fits` = height ≥ 740 dp: then nothing scrolls, the cover box takes the room that is left (`weight(1f)`, a square
    via `aspectRatio(1f)`, so it shrinks) and the lyrics card is a fixed `LYRICS_H` (120 dp) under the tools. Shorter
    phones scroll as before. The glass heart sits inside the cover's own box. Errors show between the two times.
  - **Seek bar:** `ui/components/SeekBar.kt` (Now Playing and For You): a rounded track, the loaded part
    (`PlaybackPosition.bufferedMs`, from `bufferedPosition`; PlayerConnection's ticker updates it while paused too), the
    played part as an accent gradient with a glow, a ringed thumb that grows while dragging; tap or drag to seek.
    The mini player's 2 dp line shows the loaded part as well.
  - **Same song once:** `data/SameSong.kt` (`key` / `sameAs`): same clean title (brackets, " - …", " | …", "official
    video"… dropped), same version words (remix, lofi, slowed, reverb, sped up, unplugged, acoustic, reprise,
    female/male, sad, cover, live, instrumental, karaoke, mashup, 8d, dj, extended, duet, version) and a shared singer;
    with no real singer on a side (label channels, `SameSong.LABELS`, also used by Recommendations) the length within
    5 s decides. `LibraryRepository.addToPlaylist` skips those and returns how many were added (log "playlist add:
    added N of M"); the dialog says "Already in …" / "Added N to … · M already there".
  - **Sort by** (PlaylistScreen ⋮ → "Sort by: …"): Date added, Recently added, Title, Singer, Duration. Only the view
    (and what Play / Add to queue / Export use); kept per playlist in SharedPreferences `playlist_sort`; the stored
    order and CloudSync don't change.
  - **Queue drag:** QueueSheet keeps its own `rows` (stable keys, the same song twice gets `#2`); a
    `detectDragGesturesAfterLongPress` on the LazyColumn (`QueueDrag`) moves the row under the finger, swaps it with
    the row it passes, scrolls near the edges, and calls `player.move(from, to)` once on lift. Up/down arrows are
    replaced by a drag handle ("Hold and drag to move").
  - UI walk: "Now Playing: hold and drag a song in the queue" (`input motionevent`, then reopens the queue to check the
    player's order), "Song menu: Add to playlist twice: added once" (playlist "Walk55"), "Playlist: Sort by Title".
- **App report #57 of Oct 17** (same tester, build 241: "last update me tune bahut achcha kaam kiya"): more lyrics
  ("Airlyrics" or a better open source), a singers section under Home's "Made for you" whose tile plays all of a
  singer's songs in the chosen languages, and a Settings option to pick singers next to the languages. Android only.
  - "Airlyrics" has no open API that could be found, so: **lyrics** (`data/lyrics/Lyrics.kt`) try LRCLIB with the
    song's own names, then its clean name + main singer (`SameSong.cleanTitle` / `mainSinger`, for YouTube titles
    and label channels), then LRCLIB searches (same clean name, length within 8 s, synced first); then
    **JioSaavn's own lyrics** (`JioSaavnSource.lyrics`, `lyrics.getLyrics` with the song id; often only one copy of a
    song has them, `more_info.has_lyrics`, so `lyricsFor` searches for a copy that has them: same clean name and a
    shared singer), then **lyrics.ovh** (keyless, mostly English). Source label "JioSaavn" / "lyrics.ovh"; both are
    plain (no times). CI probe Oct 17: "Tum Hi Ho" aRZbUYD7 has lyrics, its "(From Aashiqui 2)" copies don't.
  - **Singers** (`data/Singers.kt`, `container.singers`): `yours()` = `AppSettings.favoriteSingers` (key
    `favorite_singers`, "|"-joined) then the most played singers (label channels skipped); `popular()` per language;
    photos from JioSaavn's artist search, kept in SharedPreferences `singer_photos`. `songs(name)` =
    `JioSaavnSource.artistSongs`, all asked at once: `artist.getArtistPageDetails` (popular ~32, and
    `category=latest` ~49), `artist.getArtistMoreSong` pages 1–12 (only ~9 songs a page, whatever `n_song` says;
    Arijit Singh has 4683) and `search.getResults` pages 1–4 (40 each, kept when the singer is named); then only the
    chosen languages (Pahadi via `LanguageGuess.isPahadi`), one copy of each song (`SameSong`).
  - Home: "Your singers" (`SingerTile`, round photo + name; popular singers for a new listener), header action Edit →
    `Routes.SINGERS`. A tap plays all the songs shuffled ("Playing N songs by X"; log "singer: N songs by X").
  - Settings → Singers → "Favourite singers" right after Song languages (`ui/settings/SingersScreen.kt`): search,
    "Your singers", "From your listening", "Popular in your languages"; tap to pick or unpick. New song alerts use the
    picked singers first.
  - UI walk: "Settings: pick a favourite singer" (Arijit Singh) and "Home: Your singers → plays all their songs".
- **App report #63 of Oct 22** (Xiaomi 23124RN87I, build 272, Neon Glass): "jb ek song chalau bollywood ya pahadi to
  uske related hi song aana chahiye or latest and popular wale". Android only. A searched song starts a radio
  (`PlayerConnection.startRadio` → `RecommendationRepository.radio`), and the end of any queue goes on the same way.
  - **Kind** (`kindOf`): Pahadi when the name / singers / film say so (`LanguageGuess.isPahadi`) or the catalog's
    pahadi.json has the id (JioSaavn lists Pahadi songs as Hindi), else the language. The radio keeps the seed's kind:
    `rank` gives the same kind +2.5, another −1.2 (−3 after a Pahadi song), and `radio` puts all same-kind songs
    first (other songs only when those run out). The seed's kind is allowed even if it isn't a chosen language.
  - **Sources for a radio** (`candidates(p, seed, twin)`): JioSaavn's trending in the seed's language (or, for
    Pahadi, the searches `PAHADI_SEARCHES` kept by `isPahadi`), the catalog's 150 most played songs of that kind + 100
    random ones (`CatalogPool.has` = the catalog's files), and JioSaavn's `reco.getreco` of the seed or of a YouTube
    seed's JioSaavn copy (`saavnTwin`: same clean name + singer; it also gives the year / language; for a radio, a
    same-name song by others will do, for the language only: YouTube results come first in Search, and a cover on
    an unknown channel has no language). Fewer
    history-based sources (2 YouTube Music seeds, 1 JioSaavn reco, no random playlists) than the feed.
  - **Popular and new first:** `Track.plays` = JioSaavn's `play_count` (≥ 5 cr +1.5 … ≥ 1 lakh +0.3), else the
    catalog place (the files list the most played first: < 200 +1.2, < 1000 +0.6). `era`: an old seed keeps its
    years (±4 +2, > 14 years −2.5); a seed from the last 6 years or with no year also gets the newest songs (≤ 1 year
    +1, ≤ 3 +0.6). Another copy of the seed itself (`SameSong`) is left out. For You's `likeThis` uses the same.
  - Log "radio: N songs after '…' by … (kind): M the same kind". UI walk: "Search: a Bollywood song brings Hindi songs
    next" (kesariya arijit) and "Search: a Pahadi song brings Pahadi songs next" (Narendra Singh Negi) tap the first
    row under the first source header and need ≥ 8 of the same kind. The emulator test now has 60 min (the walk
    alone takes ~40) and runs the walk with `python3 -u`, so its lines show in the log as they happen.
- **App report #69 of Oct 22** (LAVA LXX525 tester, Android 16, build 283, Liquid Glass): "15-20 minute chalne ke baad
  app apne aap band ho jata hai songs bhi ruk jate hain", a singer tile should open a page first ("pahle playlist
  khule fir play button pe click ho"), and "See all" should show every picked singer. Android only. (#70, same day:
  "related song nhi arhe" = #63.)
  - **Why the app stopped:** no crash report came, so it wasn't a Java crash. `data/AppExits.kt` reads Android's own
    exit reasons (`ApplicationExitInfo`, Android 11+): every report gets "Last app exits" (time, reason, on screen /
    playing / background, MB used, Android's note). After an exit nobody asked for while on screen or playing (low
    memory, killed by the phone, battery / CPU, other) or a native crash / freeze (ANR, with the main thread's stack)
    in the last 3 days, MainActivity sends a report by itself ("Android stopped the app: …", like crash reports;
    `markSent` after it went; never on the emulator). A one-off probe (`.github/workflows/probe-longplay.yml`,
    `longplay.sh`, not merged) played 28 min on an Android 15 emulator (10 on screen, 10 in the background, 8 with
    the screen off): same process all along, memory flat at ~92 MB PSS (Java heap ~42 MB). So no leak; the phone
    ends the app. (That fresh install also ran Smart downloads at once: the emulator is "on Wi-Fi and charging".)
  - **Keep playing** (`playback/KeepPlaying.kt`): some phones' battery savers end music apps in the background.
    Once, 4 s after music first plays, a dialog "Keep music playing" → Allow opens Android's "always run in the
    background" dialog (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, permission REQUEST_IGNORE_BATTERY_OPTIMIZATIONS;
    removed in the Play manifest and hidden in the Play version). Settings → Playback → "Keep playing in the
    background" shows "Allowed" or asks again. Not on the emulator.
  - **Singers:** a tile on Home opens `ui/library/SingerScreen.kt` (route `singer/{name}`): `CollectionHeader`
    ("Singer • N songs", Play starts at a random song, Shuffle, Download all) and all `Singers.songs(name)`; log
    "singer page: N songs by X". The header action is now "See all" → `AllSingersScreen` (route `singers`): a grid
    of `Singers.yours(100)` (picked first, then most played) with an Edit button → Settings → Favourite singers.
  - UI walk: "Home: Your singers → the singer's page", "Singer page: Play plays their songs", "Home: Your singers →
    See all".
- **YouTube Shorts channel** (Oct 20, owner sent reels about "faceless" AI YouTube channels: "saare viral video dekh
  … cartoon, motion video, ai made movie … bna aur upload kar … monotize krwa"; "upload bhi tu kr, permission de
  dunga"; then "sirf shorts nhi, full movie, cartoon, ramayan, mahabharat"). Not part of the apps:
  `YouTubeAuto/make_video.py` + `.github/workflows/youtube.yml`: a Short daily 12:41 UTC, a long 16:9 video (8 chapters
  ≈ 8–12 min, outline then one Gemini call per chapter) every 2nd day 03:41 UTC. Ramayan / Mahabharat are series
  (`series`, `episode`, `recap` in history; an episode that failed to upload isn't counted). Speakers get their own
  voice (`VOICES`: pitch/rate on edge-tts' two Hindi voices). Long videos get a thumbnail (needs a phone-verified
  channel). Frames of each video go to branch `youtube-preview` (look at them before telling the owner it's good).
  The channel linked on Oct 20 is "shorts@zawwdx". First uploads (Oct 20): Short FWX2mz5Nbps (came back public, so
  the "unaudited = private" lock didn't apply), long cartoon degBJTEk_P0 (11 min, 28 real pictures of 59 in the
  150-min picture budget). Thumbnails gave 403 until the owner phone-verifies the channel (youtube.com/verify).
  Pollinations' free pictures carry a "pollinations.ai" mark at the bottom right; the bottom 7% is cut off.
  - Trends: YouTube Data API `mostPopular` IN (the app's key; its search quota is often used up, 429). Plan: Gemini
    (JSON: format from `FORMATS`, title, description, tags, voice, 7–9 scenes of Devanagari narration + English
    image prompts), new formats/topics vs `history.json`. LLM7's models now mostly 401.
  - Pictures: Pollinations (free tier gives 402 when asked too fast, so 15–20 s between asks), Gemini image (free key
    has no quota, 429), else the last real picture re-cut (`reuse`). Voice: edge-tts hi-IN Madhur/Swara, gTTS
    fallback. Video: ffmpeg zoompan per scene, ASS captions 3 words at a time (Noto Sans Devanagari), a soft drone.
  - Copies go to the release `youtube-videos` (newest 20). History + the encrypted login live on branch
    `youtube-channel`.
  - Upload: the owner made a Google Cloud OAuth client ("TVs and Limited Input devices"; Branding links point to
    `SangeetWeb/privacy.html`) → secrets `YT_CLIENT_ID` / `YT_CLIENT_SECRET`. Run the workflow by hand with "login"
    → the log shows a code for google.com/device → `token.enc` (refresh token, openssl-encrypted with the client
    secret). Uploads set `containsSyntheticMedia`. Unaudited API projects' uploads stay private until Google's audit.
  - Learns from the channel (Oct 21, owner: "tu khud dekh le trend ke hisab se"): `channel_stats` reads views /
    likes / comments of the uploaded videos (Data API, the channel login) into history (`score` = (views + 20 likes +
    40 comments) per day). `pick_format` weights formats by their average score (untried = average, 1 in 4 picks
    random, the last 2 Short / last 1 long formats skipped), and the prompts get the channel's best titles.
  - Trends, Oct 25 (owner: "trend ke hisab se"): `google_trends` reads Google Trends' India feed
    (`trends.google.com/trending/rss?geo=IN`, no key) into `TOPICS`, which both prompts get. A Short format
    `TREND_SHORT` (and the long "trend" format) is picked ~1 in 3 when topics exist, and builds the video around the
    best family-friendly topic (no politics, crimes, real people's deaths). YouTube chart / search calls use the
    channel login when there is one (its own quota), else the app's key.
  - Oct 21–24: the login gave `invalid_grant` ("Token has been expired or revoked") a day after linking, so 3 videos
    were made but not uploaded, and two runs died on Gemini 503 (LLM7's models now 401/400). Fixes: `upload_pending`
    uploads made-but-not-uploaded videos from the release copy (3 a run, ≤ 10 days old, 3 tries), `access_token`
    logs "LOGIN EXPIRED", and `ask_json` tries 10 times over up to 6 Gemini text models (`gemini_models`). If the
    login expires again, check that the OAuth app is "In production" (Testing tokens expire) and run the login.
  - Told the owner: no fake views; monetization (1000 subs + 4000 h or 10M Shorts views in 90 days) can't be promised;
    mass-produced repetitive AI videos are demonetized, so every video is a different format/story.
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

## 4b. Baatcheet (Oct 21) — separate chat / voice / video calling website

Owner pasted a long English "MASTER PROMPT" for a production chat + calling site. Built as its own project in
`Baatcheet/` (npm workspaces: `shared/`, `server/`, `web/`); it shares nothing with Sangeet. Read `Baatcheet/README.md`
first, then `Baatcheet/docs/` (API, DEPLOYMENT, TESTING with a two-device call checklist, TROUBLESHOOTING, SECURITY).
- Server: Express + Socket.IO + Prisma/PostgreSQL (`server/prisma/schema.prisma`, migration adds pg_trgm indexes),
  cookie sessions (SHA-256 of token stored), argon2id, Origin check = CSRF, signed upload/download URLs
  (`lib/storage.ts`: S3 or local folder), coturn REST credentials (`lib/turn.ts`), call state machine in
  `realtime/calls.ts` (1:1 P2P signalling relay, busy / missed / reconnect grace; groups via LiveKit tokens).
- Web: Next.js 15 App Router + Tailwind v4, own components in `components/ui`, store `lib/store.ts`, call engine
  `lib/calls.ts` (only the caller sends offers, incl. ICE restarts).
- Tests: `npm test` (53 vitest API tests on a real Postgres, DB `baatcheet_test`), `npm run e2e` (Playwright, Chromium
  fake camera: two browsers register, chat, upload, real WebRTC video call). In this sandbox run e2e with
  `PW_CHROMIUM_PATH=/opt/pw-browsers/chromium-1194/chrome-linux/chrome`. Postgres 16 is installed locally
  (`pg_ctlcluster 16 main start`, user/password baatcheet).
- TypeScript is pinned to 5.9 (TS 7 breaks `next build`: "reading 'fileExists'").
- Prisma refuses `migrate reset` from an AI agent without the owner's explicit consent; tests use `migrate deploy` +
  TRUNCATE instead. Don't bypass that guard.
- CI: `.github/workflows/baatcheet.yml` (paths `Baatcheet/**`); build-apk.yml ignores `Baatcheet/**`.
- Hosting (Oct 21): owner has no card of their own (Oracle signup failed: "information inaccurate" with a friend's
  card), domain **tbdi.in** (GoDaddy), and a Linux PC that runs 24×7. Owner's rule: "code PC me nhi rkhna, sb
  GitHub pe rahega, whi se control hoga". So: `deploy/install-home.sh` (Cloudflare Tunnel, optional Metered TURN,
  no source on the PC) runs the ghcr.io images that the `publish` job builds on green `main`; a 15-min systemd timer
  (`deploy/update.sh`) pulls new run files + images. CI jobs `home` (build mode, real trycloudflare tunnel) and
  `home-release` (only run files + published images). `deploy/install.sh` = cloud VM variant (clones the repo).
- Still needs the owner: run the installer on the PC, Brevo SMTP, Metered TURN, Cloudflare for tbdi.in.

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
| Main run 184: UI walk "Downloads screen: open" failed (branch run passed) | The check looked for the "Saved on this phone" header, which a LazyColumn doesn't draw when downloading/failed rows push it below the screen | The check reads the always-visible top line "N saved" (N ≥ 1) |

Sandbox notes for Claude Code on the web: the session proxy blocks jiosaavn.com, genius, lrclib, invidious/piped,
GitHub artifact/blob downloads and dl.google.com (no local Android SDK — build/test only in CI). Read job logs with
the GitHub MCP `get_job_logs` tool; test the web app locally with Playwright (Chromium is preinstalled) by serving
`SangeetWeb/` plus a copy of gh-pages `data/` and stubbing network routes.

## 6. Status (2026-10-11)

- `main` has everything up to PR #39 (Crystal theme, phone details in reports). Latest APK before the Oct 11 batch =
  **build 194** (emulator test passed). The site is live with the same features. The Oct 11 batch (missing songs,
  Sangeet Top 50, ringtone) is described in §4.
- App reports are checked by the routine "Check Sangeet app reports" every 2 h (dispatches reports.yml on main,
  then reads open issues labelled "app report").
- Secrets the owner set on Oct 9:
  - `GEMINI_API_KEY` (new AI Studio key; the first one was pasted in chat and then deleted by the owner).
  - A new `REPORT_TOKEN`: fine-grained, Vivek account, all repos, Issues + Contents read/write, no expiry,
    fingerprint `0688289aaba3`. The old token `2d02dbac326d` was deleted, so builds ≤ 162 can't send reports or
    uploads until they auto-update.
- Community files: CMS had no safe existing folder, so `pick_folder` put the `sangeet-*.json` files at the CMS
  repo's top level (no new folder made). Listener counts started from 0 after the one-time reset.
- The live site can't be fetched from the session sandbox (github.io is blocked). Check it in CI logs or ask the
  owner.
- The token inside the APK (REPORT_TOKEN, account vivekyadav200405-cpu) can create issues in all that account's
  repos. Advised the owner to limit it to day1 + CMS, Issues only; the owner said "koi baat nhi" (keeps it as is,
  like the YouTube key). Don't raise it again.
- YouTube background mode (web): look for `YT SERVERS: N working` in the Web App log; it was 0 on Oct 6 (public
  Invidious/Piped are blocked by YouTube) and the owner knows.
- Oct 10 batch and the report fixes (above) are in PR #36 (issues #32–#35 took the earlier numbers). The owner left out suggestion 7 (Data saver) and 9 (a repo
  `asdf968566-art.github.io` for verified App Links).
- Ideas the owner was offered but hasn't asked for: Navidrome/Subsonic option on the iPhone web app (Android has it).
- Owner asked about hosting all catalog songs on their own server: advised against it (copyright takedowns could
  hit the GitHub account; ~400–500 GB per lakh songs; no gain since JioSaavn CDN already serves them).
