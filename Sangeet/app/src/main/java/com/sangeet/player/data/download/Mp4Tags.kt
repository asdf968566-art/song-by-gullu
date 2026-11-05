package com.sangeet.player.data.download

import java.io.File
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer

/**
 * Puts the song's name, singer, album and cover into an .m4a, as iTunes-style tags (moov → udta → meta → ilst), so
 * the copy in Music/Sangeet shows them in the Files app and in other music players (owner, Nov 5: they showed a music
 * note and "<unknown>"). Only the moov box changes; when it sits before the audio (mdat), the chunk offsets in
 * stco / co64 move by as many bytes as moov grew.
 */
object Mp4Tags {
    /** Writes [src] with the tags to [out]. False (and [out] not to be used) when [src] isn't a plain MP4. */
    fun write(src: File, out: File, title: String, artist: String, album: String, cover: ByteArray?): Boolean = runCatching {
        RandomAccessFile(src, "r").use { f ->
            val len = f.length()
            val boxes = ArrayList<Box>()
            var pos = 0L
            while (pos + 8 <= len) {
                f.seek(pos)
                var size = f.readInt().toLong() and 0xFFFFFFFFL
                val type = ByteArray(4).also { f.readFully(it) }.toString(Charsets.ISO_8859_1)
                if (size == 1L) size = f.readLong() else if (size == 0L) size = len - pos
                if (size < 8 || pos + size > len) return@runCatching false
                boxes += Box(pos, size, type)
                pos += size
            }
            if (boxes.firstOrNull()?.type != "ftyp" || boxes.any { it.type == "moof" }) return@runCatching false
            val moov = boxes.firstOrNull { it.type == "moov" } ?: return@runCatching false
            val mdat = boxes.firstOrNull { it.type == "mdat" } ?: return@runCatching false
            if (moov.size > MAX_MOOV) return@runCatching false
            val old = ByteArray(moov.size.toInt()).also { f.seek(moov.offset); f.readFully(it) }
            if (ByteBuffer.wrap(old).int == 1) return@runCatching false // a 64-bit moov size: never seen in a song

            // moov's own children, without any old udta, then the new one.
            val kept = java.io.ByteArrayOutputStream()
            var p = 8
            while (p + 8 <= old.size) {
                val size = ByteBuffer.wrap(old, p, 4).int.toLong() and 0xFFFFFFFFL
                if (size < 8 || p + size > old.size) return@runCatching false
                val type = String(old, p + 4, 4, Charsets.ISO_8859_1)
                if (type != "udta") kept.write(old, p, size.toInt())
                p += size.toInt()
            }
            kept.write(udta(title, artist, album, cover))
            val body = kept.toByteArray()
            val moovNew = ByteBuffer.allocate(8 + body.size).putInt(8 + body.size).put("moov".toByteArray(Charsets.ISO_8859_1)).put(body)
            val delta = moovNew.capacity().toLong() - moov.size
            if (moov.offset < mdat.offset && delta != 0L) fixOffsets(moovNew, 8, moovNew.capacity(), delta)

            out.outputStream().buffered(1 shl 16).use { o ->
                copy(f, 0, moov.offset, o)
                o.write(moovNew.array())
                copy(f, moov.offset + moov.size, len - moov.offset - moov.size, o)
            }
            true
        }
    }.getOrDefault(false)

    private class Box(val offset: Long, val size: Long, val type: String)

    /** Moves every chunk offset of every track by [delta] bytes. */
    private fun fixOffsets(b: ByteBuffer, start: Int, end: Int, delta: Long) {
        var p = start
        while (p + 8 <= end) {
            val size = b.getInt(p).toLong() and 0xFFFFFFFFL
            if (size < 8 || p + size > end) return
            val e = p + size.toInt()
            when (String(ByteArray(4) { b.get(p + 4 + it) }, Charsets.ISO_8859_1)) {
                "trak", "mdia", "minf", "stbl" -> fixOffsets(b, p + 8, e, delta)
                "stco" -> {
                    val n = b.getInt(p + 12)
                    for (i in 0 until n) {
                        val at = p + 16 + 4 * i
                        if (at + 4 > e) break
                        b.putInt(at, ((b.getInt(at).toLong() and 0xFFFFFFFFL) + delta).toInt())
                    }
                }
                "co64" -> {
                    val n = b.getInt(p + 12)
                    for (i in 0 until n) {
                        val at = p + 16 + 8 * i
                        if (at + 8 > e) break
                        b.putLong(at, b.getLong(at) + delta)
                    }
                }
            }
            p = e
        }
    }

    private fun udta(title: String, artist: String, album: String, cover: ByteArray?): ByteArray {
        val hdlr = box("hdlr", int(0), int(0), ascii("mdir"), ascii("appl"), ByteArray(8), ByteArray(1))
        val items = listOfNotNull(
            title.takeIf { it.isNotBlank() }?.let { text("©nam", it) },
            artist.takeIf { it.isNotBlank() }?.let { text("©ART", it) },
            album.takeIf { it.isNotBlank() }?.let { text("©alb", it) },
            cover?.let { box("covr", box("data", int(JPEG), int(0), it)) },
        )
        return box("udta", box("meta", int(0), hdlr, box("ilst", *items.toTypedArray())))
    }

    private fun text(type: String, value: String) = box(type, box("data", int(UTF8), int(0), value.toByteArray(Charsets.UTF_8)))

    private fun box(type: String, vararg parts: ByteArray): ByteArray {
        val n = 8 + parts.sumOf { it.size }
        return ByteBuffer.allocate(n).putInt(n).put(ascii(type)).apply { parts.forEach { put(it) } }.array()
    }

    private fun ascii(s: String) = s.toByteArray(Charsets.ISO_8859_1)

    private fun int(v: Int) = ByteBuffer.allocate(4).putInt(v).array()

    private fun copy(f: RandomAccessFile, from: Long, count: Long, o: OutputStream) {
        val buf = ByteArray(1 shl 16)
        f.seek(from)
        var left = count
        while (left > 0) {
            val n = f.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
            if (n < 0) break
            o.write(buf, 0, n)
            left -= n
        }
    }

    private const val MAX_MOOV = 16L shl 20
    private const val UTF8 = 1
    private const val JPEG = 13
}
