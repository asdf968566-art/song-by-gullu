import SwiftUI

struct ArtworkView: View {
    let url: String?
    var size: CGFloat? = nil
    var radius: CGFloat = 6

    var body: some View {
        AsyncImage(url: url.flatMap(URL.init(string:))) { phase in
            if let image = phase.image {
                image.resizable().scaledToFill()
            } else {
                ZStack {
                    Color.white.opacity(0.08)
                    Image(systemName: "music.note").foregroundStyle(.secondary)
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: radius))
    }
}

struct TrackRow: View {
    @EnvironmentObject private var player: Player
    let track: Track

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: track.artworkURL, size: 48)
            VStack(alignment: .leading, spacing: 2) {
                Text(track.title)
                    .font(.body)
                    .foregroundStyle(player.current?.id == track.id ? Color.accent : .primary)
                    .lineLimit(1)
                Text(track.artist).font(.subheadline).foregroundStyle(.secondary).lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .contentShape(Rectangle())
        .trackMenu(track)
    }
}

/// A plain list of songs; tapping one plays the list from there.
struct TrackList: View {
    @EnvironmentObject private var player: Player
    let tracks: [Track]

    var body: some View {
        ForEach(Array(tracks.enumerated()), id: \.element.id) { i, t in
            Button { player.play(tracks, at: i) } label: { TrackRow(track: t) }
                .buttonStyle(.plain)
        }
    }
}

struct TrackMenu: ViewModifier {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var player: Player
    let track: Track

    func body(content: Content) -> some View {
        content.contextMenu {
            Button { player.playNext(track) } label: { Label("Play next", systemImage: "text.insert") }
            Button { player.addToQueue(track) } label: { Label("Add to queue", systemImage: "text.append") }
            Button { store.toggleLike(track) } label: {
                store.isLiked(track)
                    ? Label("Remove from Liked", systemImage: "heart.slash")
                    : Label("Like", systemImage: "heart")
            }
            Menu {
                Button("New playlist") { store.createPlaylist(track.title, tracks: [track]) }
                ForEach(store.playlists) { p in
                    Button(p.name) { store.add(track, to: p) }
                }
            } label: { Label("Add to playlist", systemImage: "plus") }
            if let url = shareURL {
                ShareLink(item: url) { Label("Share", systemImage: "square.and.arrow.up") }
            }
        }
    }

    private var shareURL: URL? {
        track.source == .youtube
            ? URL(string: "https://music.youtube.com/watch?v=\(track.sourceId)")
            : URL(string: "https://www.jiosaavn.com/search/song/" + (("\(track.title) \(track.artist)").addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? ""))
    }
}

extension View {
    func trackMenu(_ t: Track) -> some View { modifier(TrackMenu(track: t)) }

    func withMiniPlayer() -> some View {
        safeAreaInset(edge: .bottom, spacing: 0) { MiniPlayer() }
    }
}

struct MiniPlayer: View {
    @EnvironmentObject private var player: Player
    @State private var open = false

    var body: some View {
        if let t = player.current {
            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    ArtworkView(url: t.artworkURL, size: 42)
                    VStack(alignment: .leading, spacing: 1) {
                        Text(t.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                        Text(t.artist).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    }
                    Spacer(minLength: 0)
                    if player.isLoading {
                        ProgressView().frame(width: 36)
                    } else {
                        Button { player.toggle() } label: {
                            Image(systemName: player.isPlaying ? "pause.fill" : "play.fill").font(.title3).frame(width: 36, height: 36)
                        }
                    }
                    Button { player.next() } label: {
                        Image(systemName: "forward.fill").font(.title3).frame(width: 36, height: 36)
                    }
                }
                .foregroundStyle(.primary)
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                ProgressView(value: player.duration > 0 ? min(player.position / player.duration, 1) : 0)
                    .progressViewStyle(.linear)
                    .tint(.accent)
            }
            .background(.ultraThinMaterial)
            .contentShape(Rectangle())
            .onTapGesture { open = true }
            .sheet(isPresented: $open) { NowPlayingView() }
        }
    }
}

func formatTime(_ s: Double) -> String {
    guard s.isFinite, s > 0 else { return "0:00" }
    let t = Int(s)
    return String(format: "%d:%02d", t / 60, t % 60)
}

/// Seek bar that only moves the player when the finger lifts.
struct SeekBar: View {
    @EnvironmentObject private var player: Player
    @State private var dragging: Double?

    var body: some View {
        VStack(spacing: 2) {
            Slider(
                value: Binding(get: { dragging ?? player.position }, set: { dragging = $0 }),
                in: 0...max(player.duration, 1),
                onEditingChanged: { editing in
                    if !editing, let d = dragging {
                        player.seek(to: d)
                        dragging = nil
                    }
                }
            )
            HStack {
                Text(formatTime(dragging ?? player.position))
                Spacer()
                Text(formatTime(player.duration))
            }
            .font(.caption2.monospacedDigit())
            .foregroundStyle(.secondary)
        }
    }
}

struct Controls: View {
    @EnvironmentObject private var player: Player
    var big = true

    var body: some View {
        HStack(spacing: big ? 44 : 32) {
            Button { player.previous() } label: { Image(systemName: "backward.fill").font(big ? .title : .title2) }
            Button { player.toggle() } label: {
                ZStack {
                    if player.isLoading {
                        ProgressView()
                    } else {
                        Image(systemName: player.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                            .font(.system(size: big ? 68 : 56))
                    }
                }
                .frame(width: big ? 72 : 60, height: big ? 72 : 60)
            }
            Button { player.next() } label: { Image(systemName: "forward.fill").font(big ? .title : .title2) }
        }
        .foregroundStyle(.white)
    }
}
