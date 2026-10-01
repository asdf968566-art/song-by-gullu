import Foundation

/// One place to search all sources (YouTube metadata first, then JioSaavn).
enum Catalog {
    @MainActor
    static func search(_ query: String, store: Store) async -> [Track] {
        let key = store.youtubeKey
        async let yt: [Track] = key.isEmpty ? [] : ((try? await YouTube.search(query, key: key)) ?? [])
        async let js: [Track] = (try? await JioSaavn.search(query)) ?? []
        let (a, b) = await (yt, js)
        var seen = Set<String>()
        return interleave(a, b).filter { seen.insert($0.id).inserted }
    }

    static func interleave(_ a: [Track], _ b: [Track]) -> [Track] {
        var out: [Track] = []
        for i in 0..<max(a.count, b.count) {
            if i < a.count { out.append(a[i]) }
            if i < b.count { out.append(b[i]) }
        }
        return out
    }

    static func norm(_ s: String) -> String {
        s.lowercased()
            .replacingOccurrences(of: #"\(.*?\)|\[.*?\]"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"[^\p{L}\p{N}]+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespaces)
    }

    /// iOS can't play YouTube audio: find the same song on JioSaavn.
    static func playable(_ t: Track) async -> Track? {
        if t.source != .youtube { return t }
        let want = norm(t.title)
        guard !want.isEmpty, let results = try? await JioSaavn.search("\(t.title) \(t.artist)") else { return nil }
        return results.first { norm($0.title) == want }
            ?? results.first { norm($0.title).contains(want) || want.contains(norm($0.title)) }
    }
}
