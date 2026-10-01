import SwiftUI

struct NowPlayingView: View {
    @EnvironmentObject private var store: Store
    @EnvironmentObject private var player: Player
    @State private var showLyrics = false
    @State private var showQueue = false

    var body: some View {
        if let t = player.current {
            ZStack {
                ArtworkView(url: t.artworkURL, radius: 0)
                    .blur(radius: 50)
                    .overlay(Color.black.opacity(0.6))
                    .ignoresSafeArea()
                VStack(spacing: 20) {
                    Capsule().fill(.white.opacity(0.4)).frame(width: 40, height: 5).padding(.top, 10)
                    if showLyrics {
                        LyricsView(track: t)
                    } else {
                        Spacer(minLength: 0)
                        ArtworkView(url: t.artworkURL, radius: 14)
                            .aspectRatio(1, contentMode: .fit)
                            .padding(.horizontal, 12)
                            .shadow(radius: 20)
                        Spacer(minLength: 0)
                    }
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(t.title).font(.title3.bold()).lineLimit(1)
                            Text(t.artist).foregroundStyle(.white.opacity(0.7)).lineLimit(1)
                        }
                        Spacer()
                        Button { store.toggleLike(t) } label: {
                            Image(systemName: store.isLiked(t) ? "heart.fill" : "heart")
                                .font(.title2)
                                .foregroundStyle(store.isLiked(t) ? Color.accent : .white)
                        }
                    }
                    SeekBar()
                    Controls()
                    HStack {
                        Button { showLyrics.toggle() } label: {
                            Image(systemName: "quote.bubble").foregroundStyle(showLyrics ? Color.accent : .white)
                        }
                        Spacer()
                        Button { showQueue = true } label: { Image(systemName: "list.bullet") }
                    }
                    .font(.title3)
                    .padding(.bottom, 8)
                    if let e = player.error { Text(e).font(.caption).foregroundStyle(.secondary) }
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 28)
            }
            .sheet(isPresented: $showQueue) { QueueView() }
        } else {
            Text("Nothing playing").foregroundStyle(.secondary)
        }
    }
}

struct LyricsView: View {
    @EnvironmentObject private var player: Player
    let track: Track
    @State private var lines: [LyricLine]?

    var body: some View {
        Group {
            if let lines {
                if lines.isEmpty {
                    Text("No lyrics found").foregroundStyle(.secondary).frame(maxHeight: .infinity)
                } else {
                    let now = lines.lastIndex { $0.time <= player.position + 0.3 } ?? 0
                    ScrollViewReader { proxy in
                        ScrollView {
                            VStack(alignment: .leading, spacing: 14) {
                                ForEach(Array(lines.enumerated()), id: \.element.id) { i, l in
                                    Text(l.text.isEmpty ? "♪" : l.text)
                                        .font(.title3.bold())
                                        .foregroundStyle(i == now ? .white : .white.opacity(0.4))
                                        .id(i)
                                        .onTapGesture { player.seek(to: l.time) }
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 120)
                        }
                        .scrollIndicators(.hidden)
                        .onChange(of: now) { _, i in withAnimation { proxy.scrollTo(i, anchor: .center) } }
                    }
                }
            } else {
                ProgressView().frame(maxHeight: .infinity)
            }
        }
        .task(id: track.id) {
            lines = nil
            lines = await Lyrics.get(track)
        }
    }
}

struct QueueView: View {
    @EnvironmentObject private var player: Player

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(player.queue.enumerated()), id: \.element.id) { i, t in
                    Button { player.jump(to: i) } label: { TrackRow(track: t) }
                        .buttonStyle(.plain)
                }
            }
            .listStyle(.plain)
            .navigationTitle("Up next")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
