import Foundation

/// AI DJ: "sad punjabi songs for a night drive" -> a playlist.
/// Smart parser understands language, mood, era and artists (no account or key needed).
@MainActor
enum AiDj {
    struct Result {
        var title: String
        var tracks: [Track]
    }

    private static let moods: [([String], String)] = [
        (["sad", "dukh", "udaas", "breakup", "heartbreak", "dard"], "sad"),
        (["happy", "khush", "cheerful"], "happy"),
        (["party", "dance", "club", "naach"], "party"),
        (["romantic", "love", "pyaar", "ishq", "date"], "romantic"),
        (["chill", "lofi", "relax", "calm", "sukoon", "study"], "lofi chill"),
        (["sleep", "neend", "night", "raat"], "soft night"),
        (["gym", "workout", "running", "exercise"], "workout"),
        (["bhakti", "bhajan", "devotional", "god", "mandir"], "bhakti"),
        (["drive", "road trip", "travel", "safar"], "road trip"),
        (["rain", "barish", "baarish", "monsoon"], "barish"),
        (["wedding", "shaadi", "sangeet", "mehendi"], "wedding"),
    ]

    private static let artists = [
        "arijit singh", "shreya ghoshal", "atif aslam", "jubin nautiyal", "neha kakkar", "sonu nigam",
        "kishore kumar", "lata mangeshkar", "mohammed rafi", "kumar sanu", "udit narayan", "alka yagnik",
        "honey singh", "badshah", "diljit dosanjh", "karan aujla", "sidhu moose wala", "ap dhillon", "shubh",
        "b praak", "darshan raval", "armaan malik", "vishal mishra", "pritam", "a r rahman", "mohit chauhan",
        "sunidhi chauhan", "kk", "anuv jain", "king", "guru randhawa", "pawan singh", "masoom sharma",
    ]

    static func make(_ request: String, store: Store) async -> Result {
        let t = request.lowercased()
        var langs = Store.allLanguages.filter { t.contains($0) }
        if langs.isEmpty { langs = store.languages }
        let mood = moods.first { $0.0.contains(where: t.contains) }?.1
        let era: String
        if ["90s", "nineties", "purane", "old", "retro"].contains(where: t.contains) { era = "90s" }
        else if ["new", "naye", "latest", "2025", "2026"].contains(where: t.contains) { era = "latest" }
        else { era = "" }
        let named = artists.filter { t.contains($0) }

        var queries: [String] = []
        for a in named { queries += ["\(a) \(mood ?? "hits")", "\(a) \(era) songs"] }
        for l in langs.prefix(2) {
            queries.append("\(l) \(mood ?? "hit") songs \(era)")
            queries.append("\(l) \(mood ?? "popular") playlist")
        }
        queries.append(request)
        queries = queries.map { $0.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression).trimmingCharacters(in: .whitespaces) }

        var results: [[Track]] = []
        await withTaskGroup(of: [Track].self) { group in
            for q in Array(Set(queries)) { group.addTask { (try? await JioSaavn.search(q)) ?? [] } }
            for await r in group { results.append(Array(r.prefix(12))) }
        }
        var merged: [Track] = []
        for i in 0..<(results.map(\.count).max() ?? 0) { for r in results where i < r.count { merged.append(r[i]) } }
        var unique = Set<String>()
        var tracks = merged.filter { unique.insert($0.id).inserted && $0.inLanguages(langs) }
        if tracks.count < 40, let seed = tracks.first, let more = try? await JioSaavn.similar(seed.sourceId) {
            tracks += more.filter { unique.insert($0.id).inserted && $0.inLanguages(langs) }
        }
        return Result(title: request.prefix(1).uppercased() + request.dropFirst().prefix(39), tracks: Array(tracks.prefix(60)))
    }
}
