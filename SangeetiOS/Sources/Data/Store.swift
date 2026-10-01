import Foundation
import SwiftUI

struct PlaylistModel: Codable, Identifiable, Hashable {
    var id = UUID()
    var name: String
    var tracks: [Track]
}

struct PlayRecord: Codable {
    var track: Track
    var count: Int
    var last: Date
}

/// Everything saved on the phone: likes, playlists, history, seen songs, settings.
@MainActor
final class Store: ObservableObject {
    static let defaultYouTubeKey = "AIzaSyB1kFxcI23xi418PgROIaGhT_-p2DG33Pw"

    @Published var liked: [Track] = [] { didSet { save("liked", liked) } }
    @Published var playlists: [PlaylistModel] = [] { didSet { save("playlists", playlists) } }
    @Published var history: [String: PlayRecord] = [:] { didSet { save("history", history) } }
    @Published var languages: [String] = ["hindi", "punjabi"] { didSet { save("languages", languages) } }
    @Published var youtubeKey: String = Store.defaultYouTubeKey { didSet { save("ytKey", youtubeKey) } }
    @Published var quality: AudioQuality = .high { didSet { save("quality", quality) } }
    private(set) var seen: [String] = []
    private var seenSet = Set<String>()

    static let allLanguages = ["hindi", "punjabi", "english", "haryanvi", "bhojpuri", "tamil", "telugu", "marathi", "bengali", "gujarati"]

    init() {
        liked = load("liked") ?? []
        playlists = load("playlists") ?? []
        history = load("history") ?? [:]
        languages = load("languages") ?? ["hindi", "punjabi"]
        youtubeKey = load("ytKey") ?? Store.defaultYouTubeKey
        quality = load("quality") ?? .high
        seen = load("seen") ?? []
        seenSet = Set(seen)
    }

    func isLiked(_ t: Track) -> Bool { liked.contains { $0.id == t.id } }

    func toggleLike(_ t: Track) {
        if let i = liked.firstIndex(where: { $0.id == t.id }) { liked.remove(at: i) } else { liked.insert(t, at: 0) }
    }

    func recordPlay(_ t: Track) {
        var r = history[t.id] ?? PlayRecord(track: t, count: 0, last: .now)
        r.count += 1
        r.last = .now
        history[t.id] = r
        markSeen([t.id])
    }

    var recent: [Track] { history.values.sorted { $0.last > $1.last }.map(\.track) }

    // "Seen": a song that was shown or played never comes back automatically.
    func markSeen(_ ids: [String]) {
        for id in ids where seenSet.insert(id).inserted { seen.append(id) }
        if seen.count > 20_000 {
            let drop = seen.prefix(seen.count - 20_000)
            drop.forEach { seenSet.remove($0) }
            seen.removeFirst(drop.count)
        }
        save("seen", seen)
    }

    func isSeen(_ id: String) -> Bool { seenSet.contains(id) }

    func clearSeen() {
        seen = []
        seenSet = []
        save("seen", seen)
    }

    func createPlaylist(_ name: String, tracks: [Track]) {
        playlists.insert(PlaylistModel(name: name.isEmpty ? "My playlist" : name, tracks: tracks), at: 0)
    }

    func add(_ t: Track, to playlist: PlaylistModel) {
        guard let i = playlists.firstIndex(of: playlist), !playlists[i].tracks.contains(where: { $0.id == t.id }) else { return }
        playlists[i].tracks.append(t)
    }

    private func save<T: Encodable>(_ key: String, _ value: T) {
        if let data = try? JSONEncoder().encode(value) { UserDefaults.standard.set(data, forKey: key) }
    }

    private func load<T: Decodable>(_ key: String) -> T? {
        guard let data = UserDefaults.standard.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }
}
