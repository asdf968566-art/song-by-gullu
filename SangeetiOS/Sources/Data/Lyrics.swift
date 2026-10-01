import Foundation

struct LyricLine: Identifiable {
    let id = UUID()
    let time: Double
    let text: String
}

/// Synced lyrics from LRCLIB (free).
enum Lyrics {
    private static var cache: [String: [LyricLine]] = [:]

    @MainActor
    static func get(_ t: Track) async -> [LyricLine] {
        if let c = cache[t.id] { return c }
        var q = ["track_name": t.title, "artist_name": t.artist.components(separatedBy: ",").first ?? t.artist]
        if t.durationSec > 0 { q["duration"] = String(Int(t.durationSec)) }
        var synced: String?
        if let o = try? await Net.json(Net.url("https://lrclib.net/api/get", q)) as? [String: Any] {
            synced = o.str("syncedLyrics")
        }
        if synced == nil,
           let arr = try? await Net.json(Net.url("https://lrclib.net/api/search", ["track_name": t.title, "artist_name": q["artist_name"] ?? ""])) as? [[String: Any]] {
            synced = arr.compactMap { $0.str("syncedLyrics") }.first
        }
        let lines = parse(synced ?? "")
        cache[t.id] = lines
        return lines
    }

    static func parse(_ text: String) -> [LyricLine] {
        let re = try! NSRegularExpression(pattern: #"\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?\]"#)
        var out: [LyricLine] = []
        for raw in text.components(separatedBy: .newlines) {
            let ns = raw as NSString
            let matches = re.matches(in: raw, range: NSRange(location: 0, length: ns.length))
            guard let last = matches.last else { continue }
            let lyric = ns.substring(from: last.range.location + last.range.length).trimmingCharacters(in: .whitespaces)
            for m in matches {
                let min = Double(ns.substring(with: m.range(at: 1))) ?? 0
                let sec = Double(ns.substring(with: m.range(at: 2))) ?? 0
                var frac = 0.0
                if m.range(at: 3).location != NSNotFound {
                    let f = ns.substring(with: m.range(at: 3))
                    frac = (Double(f) ?? 0) / pow(10, Double(f.count))
                }
                out.append(LyricLine(time: min * 60 + sec + frac, text: lyric))
            }
        }
        return out.sorted { $0.time < $1.time }
    }
}
