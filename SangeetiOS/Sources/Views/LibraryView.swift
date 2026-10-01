import SwiftUI

struct LibraryView: View {
    @EnvironmentObject private var store: Store
    @State private var charts: [OnlinePlaylist] = []
    @State private var newName = ""
    @State private var askName = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    NavigationLink { DjView() } label: { Label("AI DJ", systemImage: "sparkles") }
                    NavigationLink { SongsScreen(title: "Liked Songs", tracks: store.liked) } label: {
                        Label("Liked Songs", systemImage: "heart.fill")
                    }
                    NavigationLink { SongsScreen(title: "Recently Played", tracks: Array(store.recent.prefix(200))) } label: {
                        Label("Recently Played", systemImage: "clock")
                    }
                    NavigationLink { OnlinePlaylistsView() } label: { Label("Online Library", systemImage: "globe") }
                }
                Section("Playlists") {
                    ForEach(store.playlists) { p in
                        NavigationLink { SongsScreen(title: p.name, tracks: p.tracks) } label: {
                            VStack(alignment: .leading) {
                                Text(p.name)
                                Text(p.tracks.count == 1 ? "1 song" : "\(p.tracks.count) songs").font(.caption).foregroundStyle(.secondary)
                            }
                        }
                    }
                    .onDelete { store.playlists.remove(atOffsets: $0) }
                    Button { askName = true } label: { Label("New playlist", systemImage: "plus") }
                }
                if !charts.isEmpty {
                    Section("Top Charts") {
                        ForEach(charts) { p in
                            NavigationLink { OnlinePlaylistView(playlist: p) } label: { PlaylistRow(playlist: p) }
                        }
                    }
                }
            }
            .navigationTitle("Library")
            .task { if charts.isEmpty { charts = (try? await JioSaavn.charts(langs: store.languages)) ?? [] } }
            .alert("New playlist", isPresented: $askName) {
                TextField("Name", text: $newName)
                Button("Create") { store.createPlaylist(newName, tracks: []); newName = "" }
                Button("Cancel", role: .cancel) { newName = "" }
            }
        }
    }
}

struct PlaylistRow: View {
    let playlist: OnlinePlaylist
    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: playlist.artworkURL, size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(playlist.title).lineLimit(1)
                if !playlist.subtitle.isEmpty {
                    Text(playlist.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                }
            }
        }
    }
}

struct SongsScreen: View {
    @EnvironmentObject private var player: Player
    let title: String
    let tracks: [Track]

    var body: some View {
        List {
            if tracks.isEmpty {
                Text("No songs yet").foregroundStyle(.secondary)
            } else {
                HStack(spacing: 12) {
                    Button { player.play(tracks) } label: { Label("Play", systemImage: "play.fill").frame(maxWidth: .infinity) }
                    Button { player.play(tracks.shuffled()) } label: { Label("Shuffle", systemImage: "shuffle").frame(maxWidth: .infinity) }
                }
                .buttonStyle(.bordered)
                .listRowSeparator(.hidden)
                TrackList(tracks: tracks)
            }
        }
        .listStyle(.plain)
        .navigationTitle(title)
    }
}

/// Thousands of JioSaavn playlists, loaded page by page.
struct OnlinePlaylistsView: View {
    @EnvironmentObject private var store: Store
    @State private var items: [OnlinePlaylist] = []
    @State private var page = 1
    @State private var done = false
    @State private var loading = false

    var body: some View {
        List {
            ForEach(items) { p in
                NavigationLink { OnlinePlaylistView(playlist: p) } label: { PlaylistRow(playlist: p) }
                    .onAppear { if p.id == items.last?.id { Task { await more() } } }
            }
            if loading { HStack { Spacer(); ProgressView(); Spacer() } }
        }
        .listStyle(.plain)
        .navigationTitle("Online Library")
        .task { if items.isEmpty { await more() } }
    }

    private func more() async {
        if loading || done { return }
        loading = true
        let list = (try? await JioSaavn.featuredPlaylists(langs: store.languages, page: page)) ?? []
        let have = Set(items.map(\.id))
        let fresh = list.filter { !have.contains($0.id) }
        items += fresh
        page += 1
        done = fresh.isEmpty
        loading = false
    }
}

struct OnlinePlaylistView: View {
    @EnvironmentObject private var store: Store
    let playlist: OnlinePlaylist
    @State private var tracks: [Track]?

    var body: some View {
        Group {
            if let tracks {
                SongsScreen(title: playlist.title, tracks: tracks)
                    .toolbar {
                        Button { store.createPlaylist(playlist.title, tracks: tracks) } label: { Image(systemName: "plus.square.on.square") }
                    }
            } else {
                ProgressView().navigationTitle(playlist.title)
            }
        }
        .task { if tracks == nil { tracks = (try? await JioSaavn.playlistTracks(playlist.id)) ?? [] } }
    }
}
