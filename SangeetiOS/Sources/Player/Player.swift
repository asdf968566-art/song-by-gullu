import AVFoundation
import MediaPlayer
import SwiftUI

/// The music player: a queue on top of AVPlayer, lock-screen controls and endless autoplay.
@MainActor
final class Player: ObservableObject {
    @Published private(set) var queue: [Track] = []
    @Published private(set) var index: Int = -1
    @Published private(set) var isPlaying = false
    @Published private(set) var isLoading = false
    @Published private(set) var position: Double = 0
    @Published private(set) var duration: Double = 0
    @Published var error: String?

    var current: Track? { queue.indices.contains(index) ? queue[index] : nil }

    private let store: Store
    private let recommender: Recommender
    private let player = AVPlayer()
    private var timeObserver: Any?
    private var endObserver: NSObjectProtocol?
    private var loadTask: Task<Void, Never>?
    private var autoplayTask: Task<Void, Never>?
    private var artwork: (String, MPMediaItemArtwork)?

    init(store: Store) {
        self.store = store
        self.recommender = Recommender(store: store)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .default)
        player.automaticallyWaitsToMinimizeStalling = true
        timeObserver = player.addPeriodicTimeObserver(forInterval: CMTime(seconds: 0.5, preferredTimescale: 600), queue: .main) { [weak self] time in
            MainActor.assumeIsolated { self?.tick(time) }
        }
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: nil, queue: .main) { [weak self] note in
            MainActor.assumeIsolated {
                guard let self, (note.object as? AVPlayerItem) === self.player.currentItem else { return }
                self.next()
            }
        }
        setupRemoteCommands()
    }

    // MARK: Queue

    /// Play a list starting at `index`. Autoplay continues with fresh songs after the list ends.
    func play(_ tracks: [Track], at index: Int = 0) {
        guard tracks.indices.contains(index) else { return }
        queue = tracks
        load(index)
    }

    func playNext(_ t: Track) {
        if current == nil { play([t]); return }
        queue.removeAll { $0.id == t.id && $0.id != current?.id }
        queue.insert(t, at: min(index + 1, queue.count))
    }

    func addToQueue(_ t: Track) {
        if current == nil { play([t]); return }
        if !queue.contains(where: { $0.id == t.id }) { queue.append(t) }
    }

    /// Append songs without interrupting playback (used by the For You feed).
    func append(_ tracks: [Track]) {
        let have = Set(queue.map(\.id))
        queue += tracks.filter { !have.contains($0.id) }
    }

    func jump(to i: Int) { if queue.indices.contains(i) { load(i) } }

    func next() {
        if index + 1 < queue.count { load(index + 1); return }
        // Queue finished: never repeat automatically, fetch fresh songs instead.
        autoplay(thenPlay: true)
    }

    func previous() {
        if position > 3 || index == 0 { seek(to: 0); return }
        load(index - 1)
    }

    func toggle() {
        if current == nil { return }
        if isPlaying { pause() } else { resume() }
    }

    func pause() {
        player.pause()
        isPlaying = false
        updateNowPlaying()
    }

    func resume() {
        if player.currentItem == nil { load(index); return }
        try? AVAudioSession.sharedInstance().setActive(true)
        player.play()
        isPlaying = true
        updateNowPlaying()
    }

    func seek(to seconds: Double) {
        position = seconds
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
        updateNowPlaying()
    }

    // MARK: Loading

    private func load(_ i: Int) {
        loadTask?.cancel()
        index = i
        position = 0
        duration = queue[i].durationSec
        isLoading = true
        error = nil
        player.pause()
        updateNowPlaying()
        let wanted = queue[i]
        loadTask = Task { [weak self] in
            guard let self else { return }
            let playable = await Catalog.playable(wanted)
            guard !Task.isCancelled, self.current?.id == wanted.id else { return }
            guard let p = playable, let url = JioSaavn.streamURL(p, quality: self.store.quality) else {
                self.isLoading = false
                self.error = "Couldn't play \"\(wanted.title)\""
                self.skipAfterError()
                return
            }
            if wanted.source == .youtube, p.durationSec > 0 { self.duration = p.durationSec }
            try? AVAudioSession.sharedInstance().setActive(true)
            self.player.replaceCurrentItem(with: AVPlayerItem(url: url))
            self.player.play()
            self.isPlaying = true
            self.isLoading = false
            self.store.recordPlay(wanted)
            self.updateNowPlaying()
            if self.queue.count - self.index <= 3 { self.autoplay(thenPlay: false) }
        }
    }

    private func skipAfterError() {
        Task { [weak self] in
            try? await Task.sleep(for: .seconds(1.5))
            guard let self, self.error != nil else { return }
            self.next()
        }
    }

    private func autoplay(thenPlay: Bool) {
        if autoplayTask != nil { return }
        let exclude = Set(queue.map(\.id))
        autoplayTask = Task { [weak self] in
            guard let self else { return }
            let fresh = await self.recommender.suggestions(count: 20, exclude: exclude)
            self.autoplayTask = nil
            guard !fresh.isEmpty else {
                if thenPlay { self.pause() }
                return
            }
            self.store.markSeen(fresh.map(\.id))
            self.append(fresh)
            if thenPlay, self.index + 1 < self.queue.count { self.load(self.index + 1) }
        }
    }

    private func tick(_ time: CMTime) {
        guard !isLoading else { return }
        position = time.seconds.isFinite ? time.seconds : 0
        if let d = player.currentItem?.duration.seconds, d.isFinite, d > 0 { duration = d }
        if player.currentItem?.status == .failed, error == nil, let t = current {
            error = "Couldn't play \"\(t.title)\""
            skipAfterError()
        }
    }

    // MARK: Lock screen

    private func setupRemoteCommands() {
        let c = MPRemoteCommandCenter.shared()
        c.playCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.resume() }; return .success }
        c.pauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.pause() }; return .success }
        c.togglePlayPauseCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.toggle() }; return .success }
        c.nextTrackCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.next() }; return .success }
        c.previousTrackCommand.addTarget { [weak self] _ in MainActor.assumeIsolated { self?.previous() }; return .success }
        c.changePlaybackPositionCommand.addTarget { [weak self] e in
            guard let e = e as? MPChangePlaybackPositionCommandEvent else { return .commandFailed }
            let t = e.positionTime
            MainActor.assumeIsolated { self?.seek(to: t) }
            return .success
        }
    }

    nonisolated private static func makeArtwork(_ image: UIImage) -> MPMediaItemArtwork {
        MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }

    private func updateNowPlaying() {
        guard let t = current else {
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
            return
        }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: t.title,
            MPMediaItemPropertyArtist: t.artist,
            MPMediaItemPropertyAlbumTitle: t.album,
            MPMediaItemPropertyPlaybackDuration: duration,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: position,
            MPNowPlayingInfoPropertyPlaybackRate: isPlaying ? 1.0 : 0.0,
        ]
        if let a = artwork, a.0 == t.id { info[MPMediaItemPropertyArtwork] = a.1 }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
        if artwork?.0 != t.id, let s = t.artworkURL, let url = URL(string: s) {
            let id = t.id
            Task { [weak self] in
                guard let (data, _) = try? await Net.session.data(from: url), let image = UIImage(data: data) else { return }
                let art = Player.makeArtwork(image)
                guard let self, self.current?.id == id else { return }
                self.artwork = (id, art)
                self.updateNowPlaying()
            }
        }
    }
}
