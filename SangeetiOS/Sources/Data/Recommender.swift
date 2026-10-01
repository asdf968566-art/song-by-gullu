import Foundation

/// Picks fresh songs from your history, likes and languages. Never repeats a seen song.
@MainActor
final class Recommender {
    private let store: Store
    init(store: Store) { self.store = store }

    struct Profile {
        var artists: [String: Double] = [:]
        var languages: [String] = []
        var seeds: [Track] = []
    }

    func profile() -> Profile {
        var p = Profile()
        var langScore: [String: Double] = [:]
        for r in store.history.values {
            let age = Date.now.timeIntervalSince(r.last) / 86_400
            let w = Double(r.count) * (0.5 + 1 / (1 + age / 7))
            for a in splitArtists(r.track.artist) { p.artists[Catalog.norm(a), default: 0] += w }
            if !r.track.language.isEmpty { langScore[r.track.language, default: 0] += Double(r.count) }
        }
        for t in store.liked {
            for a in splitArtists(t.artist) { p.artists[Catalog.norm(a), default: 0] += 3 }
            if !t.language.isEmpty { langScore[t.language, default: 0] += 3 }
        }
        let fromHistory = langScore.sorted { $0.value > $1.value }.map(\.key)
        p.languages = Array(NSOrderedSet(array: fromHistory + store.languages)) as? [String] ?? store.languages
        p.seeds = (store.liked + store.recent).filter { $0.source == .jiosaavn }.shuffled()
        return p
    }

    /// Fresh songs for the feed / autoplay.
    func suggestions(count: Int = 25, exclude: Set<String> = []) async -> [Track] {
        let p = profile()
        let langs = store.languages
        var pool: [Track] = []

        await withTaskGroup(of: [Track].self) { group in
            for seed in p.seeds.prefix(5) {
                group.addTask { (try? await JioSaavn.similar(seed.sourceId)) ?? [] }
            }
            for lang in p.languages.prefix(2) {
                group.addTask { (try? await JioSaavn.trending(language: lang)) ?? [] }
            }
            let topArtists = p.artists.sorted { $0.value > $1.value }.prefix(4).map(\.key)
            for a in topArtists {
                group.addTask { (try? await JioSaavn.search(a, page: Int.random(in: 1...3))) ?? [] }
            }
            group.addTask {
                let page = Int.random(in: 1...25)
                guard let pl = (try? await JioSaavn.featuredPlaylists(langs: langs, page: page))?.randomElement() else { return [] }
                return (try? await JioSaavn.playlistTracks(pl.id)) ?? []
            }
            for await list in group { pool += list }
        }

        var unique = Set<String>()
        let fresh = pool.filter { t in
            unique.insert(t.id).inserted && !exclude.contains(t.id) && !store.isSeen(t.id) && t.inLanguages(langs)
        }
        // Score: favourite artists first, plenty of randomness for variety.
        let ranked = fresh.map { t -> (Track, Double) in
            let a = splitArtists(t.artist).map { p.artists[Catalog.norm($0)] ?? 0 }.max() ?? 0
            return (t, log(1 + a) + Double.random(in: 0...2.5))
        }.sorted { $0.1 > $1.1 }.map(\.0)
        return spreadArtists(ranked).prefix(count).map { $0 }
    }

    private func spreadArtists(_ list: [Track]) -> [Track] {
        var pending = list
        var out: [Track] = []
        while !pending.isEmpty {
            let last = out.last.map { Catalog.norm($0.artist) }
            let i = pending.firstIndex { Catalog.norm($0.artist) != last } ?? 0
            out.append(pending.remove(at: i))
        }
        return out
    }
}

func splitArtists(_ s: String) -> [String] {
    s.components(separatedBy: CharacterSet(charactersIn: ",&"))
        .map { $0.trimmingCharacters(in: .whitespaces) }
        .filter { $0.count > 1 }
}
