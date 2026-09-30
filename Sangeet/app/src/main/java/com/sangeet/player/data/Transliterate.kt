package com.sangeet.player.data

/** Devanagari (हिंदी) ko Hinglish mein: "तुम ही हो" -> "tum hi ho", "केसरिया" -> "kesariya". */
object Transliterate {
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "i", 'उ' to "u", 'ऊ' to "u", 'ऋ' to "ri",
        'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
    )
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h",
        '\u0958' to "q", '\u0959' to "kh", '\u095A' to "g", '\u095B' to "z", '\u095C' to "r", '\u095D' to "rh", '\u095E' to "f", '\u095F' to "y",
    )
    private val matras = mapOf(
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u", 'ृ' to "ri",
        'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
    )
    private const val VIRAMA = '्'
    private const val NUKTA = '़'

    fun hasDevanagari(s: String) = s.any { it in 'ऀ'..'ॿ' }

    fun toLatin(text: String): String = text.split(Regex("\\s+")).joinToString(" ") { word(it) }.trim()

    private fun word(w: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < w.length) {
            var ch = w[i]
            // nukta wale akshar (क़) ko ek hi maano
            if (i + 1 < w.length && w[i + 1] == NUKTA) {
                ch = when (ch) { 'क' -> '\u0958'; 'ख' -> '\u0959'; 'ग' -> '\u095A'; 'ज' -> '\u095B'; 'ड' -> '\u095C'; 'ढ' -> '\u095D'; 'फ' -> '\u095E'; else -> ch }
                i++
            }
            val cons = consonants[ch]
            when {
                cons != null -> {
                    out.append(cons)
                    val next = w.getOrNull(i + 1)
                    when {
                        next != null && matras.containsKey(next) -> { out.append(matras[next]); i++ }
                        next == VIRAMA -> i++
                        // shabd ke aakhir ka "a" nahi bolte (tum, not tuma)
                        next == null || next == 'ं' || next == 'ँ' -> if (next != null) out.append('a')
                        else -> out.append('a')
                    }
                }
                vowels.containsKey(ch) -> out.append(vowels[ch])
                ch == 'ं' || ch == 'ँ' -> out.append('n')
                ch == 'ः' -> out.append('h')
                ch in '०'..'९' -> out.append('0' + (ch - '०'))
                ch in 'ऀ'..'ॿ' -> Unit
                else -> out.append(ch)
            }
            i++
        }
        return out.toString()
    }
}
