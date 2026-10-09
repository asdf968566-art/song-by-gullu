package com.sangeet.player.data

/**
 * Hindi (Devanagari) and Punjabi (Gurmukhi) in Latin letters, the way song names are spelt: "तुम ही हो" -> "tum hi ho",
 * "केसरिया" -> "kesariya", "ਜੱਟ" -> "jatt". [variants] gives a few spellings (names keep long vowels or not: "rabta" /
 * "raabta"; the unsaid a inside a word: "dhadakan" / "dhadkan"). Same rules as the web app's Translit.
 */
object Transliterate {
    private val consonants = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "n", 'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n", 'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m", 'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v",
        'श' to "sh", 'ष' to "sh", 'स' to "s", 'ह' to "h", 'ळ' to "l",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "d", 'ढ़' to "dh", 'फ़' to "f", 'य़' to "y",
        'ਕ' to "k", 'ਖ' to "kh", 'ਗ' to "g", 'ਘ' to "gh", 'ਙ' to "n", 'ਚ' to "ch", 'ਛ' to "chh", 'ਜ' to "j", 'ਝ' to "jh", 'ਞ' to "n",
        'ਟ' to "t", 'ਠ' to "th", 'ਡ' to "d", 'ਢ' to "dh", 'ਣ' to "n", 'ਤ' to "t", 'ਥ' to "th", 'ਦ' to "d", 'ਧ' to "dh", 'ਨ' to "n",
        'ਪ' to "p", 'ਫ' to "ph", 'ਬ' to "b", 'ਭ' to "bh", 'ਮ' to "m", 'ਯ' to "y", 'ਰ' to "r", 'ਲ' to "l", 'ਵ' to "v",
        'ਸ' to "s", 'ਹ' to "h", 'ੜ' to "d", 'ਸ਼' to "sh", 'ਖ਼' to "kh", 'ਗ਼' to "g", 'ਜ਼' to "z", 'ਫ਼' to "f", 'ਲ਼' to "l",
    )
    /** A letter with a nukta (क़, ਸ਼) as one letter. */
    private val nukta = mapOf(
        'क' to 'क़', 'ख' to 'ख़', 'ग' to 'ग़', 'ज' to 'ज़', 'ड' to 'ड़', 'ढ' to 'ढ़', 'फ' to 'फ़',
        'ਸ' to 'ਸ਼', 'ਖ' to 'ਖ਼', 'ਗ' to 'ਗ਼', 'ਜ' to 'ਜ਼', 'ਫ' to 'ਫ਼', 'ਲ' to 'ਲ਼',
    )
    // A / I / U: long vowels, until the spelling is picked.
    private val vowels = mapOf(
        'अ' to "a", 'आ' to "A", 'इ' to "i", 'ई' to "I", 'उ' to "u", 'ऊ' to "U", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au", 'ऑ' to "o",
        'ਅ' to "a", 'ਆ' to "A", 'ਇ' to "i", 'ਈ' to "I", 'ਉ' to "u", 'ਊ' to "U", 'ਏ' to "e", 'ਐ' to "ai", 'ਓ' to "o", 'ਔ' to "au",
    )
    private val matras = mapOf(
        'ा' to "A", 'ि' to "i", 'ी' to "I", 'ु' to "u", 'ू' to "U", 'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au", 'ॉ' to "o", 'ॅ' to "e",
        'ਾ' to "A", 'ਿ' to "i", 'ੀ' to "I", 'ੁ' to "u", 'ੂ' to "U", 'ੇ' to "e", 'ੈ' to "ai", 'ੋ' to "o", 'ੌ' to "au",
    )
    private val viramas = setOf('्', '੍')
    private val nuktas = setOf('़', '਼')
    private val nasals = setOf('ं', 'ँ', 'ਂ', 'ੰ')

    private fun indic(c: Char) = c in 'ऀ'..'ॿ' || c in '਀'..'੿'

    fun hasIndic(s: String) = s.any(::indic)

    /** The usual spelling ("tum hi ho"). */
    fun toLatin(text: String): String = variants(text).firstOrNull().orEmpty()

    /** A few spellings, the usual one first: as written, with long vowels, without the unsaid a's. */
    fun variants(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }.map(::units)
        fun spell(long: Boolean, drop: Boolean) = words.joinToString(" ") { render(it, long, drop) }.trim()
        return listOf(spell(false, false), spell(true, false), spell(false, true)).filter { it.isNotBlank() }.distinct()
    }

    /** A letter: consonant, vowel ("" = none), [inh] = the vowel is the unwritten a, [n] = a nasal after it. */
    private class Letter(val c: String, var v: String, var inh: Boolean, var n: String = "")

    private fun units(w: String): List<Letter> {
        val out = ArrayList<Letter>()
        var double = false
        var i = 0
        while (i < w.length) {
            var ch = w[i]
            if (i + 1 < w.length && w[i + 1] in nuktas && nukta.containsKey(ch)) { ch = nukta.getValue(ch); i++ }
            val c = consonants[ch]
            when {
                c != null -> {
                    val u = Letter(if (double && c != "ch") c.take(1) + c else c, "a", true)
                    double = false
                    val next = w.getOrNull(i + 1)
                    if (next != null && matras.containsKey(next)) { u.v = matras.getValue(next); u.inh = false; i++ }
                    else if (next != null && next in viramas) { u.v = ""; u.inh = false; i++ }
                    out += u
                }
                vowels.containsKey(ch) -> out += Letter("", vowels.getValue(ch), false)
                ch in nasals -> if (out.isNotEmpty()) out.last().n = "n" else out += Letter("n", "", false)
                ch == 'ः' -> out += Letter("h", "", false)
                ch == 'ੱ' -> double = true // addak: the next letter is said twice (ਜੱਟ = jatt)
                ch in '०'..'९' -> out += Letter(('0' + (ch - '०')).toString(), "", false)
                ch in '੦'..'੯' -> out += Letter(('0' + (ch - '੦')).toString(), "", false)
                indic(ch) -> Unit
                else -> out += Letter(ch.toString(), "", false)
            }
            i++
        }
        return out
    }

    /** [long]: aa/ee/oo (not at a word's end). [drop]: the unsaid a inside a word too. The a at the end is never said. */
    private fun render(us: List<Letter>, long: Boolean, drop: Boolean): String = buildString {
        fun has(u: Letter?) = u != null && u.v.isNotEmpty()
        us.forEachIndexed { i, u ->
            var v = u.v
            val last = i == us.lastIndex
            val next = us.getOrNull(i + 1)
            val nextFinalSchwa = next != null && next.inh && i + 1 == us.lastIndex && next.n.isEmpty()
            if (u.inh && us.size > 1 && last && u.n.isEmpty()) v = ""
            else if (u.inh && drop && i > 0 && !last && u.n.isEmpty() && has(us[i - 1]) && next!!.c.isNotEmpty() && has(next) && !nextFinalSchwa) v = ""
            if (long && !last) v = v.replace("A", "aa").replace("I", "ee").replace("U", "oo")
            append(u.c).append(v.lowercase()).append(u.n)
        }
    }
}
