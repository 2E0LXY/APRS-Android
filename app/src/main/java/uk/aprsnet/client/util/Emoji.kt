package uk.aprsnet.client.util

/**
 * Smiley / shortcode -> Unicode emoji replacement, applied at DISPLAY TIME only.
 *
 * APRS messages are transmitted as 7-bit ASCII with a ~67-character limit, so
 * we never store or transmit Unicode emoji in the wire payload. Instead, the
 * sender types an ASCII smiley (e.g. ":)"), the wire format keeps that ASCII
 * intact, and clients that understand the replacement render the matching
 * emoji on screen. Recipients on classic APRS clients still see ":)" - no
 * data loss, no broken packets.
 *
 * The same mapping is mirrored in the web map's JS so messages render the
 * same way in both surfaces.
 *
 * Mappings are intentionally conservative to avoid false positives:
 *  - ":/" is NOT mapped (collides with URLs like https://example.com).
 *  - ":N" / numeric forms are NOT mapped (collides with grid references).
 *  - Patterns are ordered LONGEST FIRST so ":-)" doesn't get partially
 *    matched as ":-" + ")".
 *  - All matches require a word boundary on the LEFT (start of string or
 *    whitespace) to avoid catching ":)" inside e.g. "https://example.com)".
 */
object Emoji {

    // Ordered longest-first. Each entry is plain text -> emoji.
    private val MAP: List<Pair<String, String>> = listOf(
        // Three-character composites first
        ":-D" to "\uD83D\uDE00",   // 😀
        ":-)" to "\uD83D\uDE42",   // 🙂
        ":-(" to "\uD83D\uDE41",   // 🙁
        ":-P" to "\uD83D\uDE1B",   // 😛
        ":-p" to "\uD83D\uDE1B",   // 😛
        ":-O" to "\uD83D\uDE2E",   // 😮
        ":-o" to "\uD83D\uDE2E",   // 😮
        ":-|" to "\uD83D\uDE10",   // 😐
        ";-)" to "\uD83D\uDE09",   // 😉
        ":'(" to "\uD83D\uDE22",   // 😢
        // Two-character
        ":)" to "\uD83D\uDE42",    // 🙂
        ":(" to "\uD83D\uDE41",    // 🙁
        ":D" to "\uD83D\uDE00",    // 😀
        ":P" to "\uD83D\uDE1B",    // 😛
        ":p" to "\uD83D\uDE1B",    // 😛
        ":O" to "\uD83D\uDE2E",    // 😮
        ":o" to "\uD83D\uDE2E",    // 😮
        ":|" to "\uD83D\uDE10",    // 😐
        ";)" to "\uD83D\uDE09",    // 😉
        "xD" to "\uD83D\uDE06",    // 😆
        "XD" to "\uD83D\uDE06",    // 😆
        "<3" to "\u2764\uFE0F",    // ❤️
        // Slack-style shortcodes (case-sensitive)
        ":smile:"     to "\uD83D\uDE04",
        ":grin:"      to "\uD83D\uDE01",
        ":laugh:"     to "\uD83D\uDE02",
        ":heart:"     to "\u2764\uFE0F",
        ":thumbsup:"  to "\uD83D\uDC4D",
        ":+1:"        to "\uD83D\uDC4D",
        ":thumbsdown:" to "\uD83D\uDC4E",
        ":-1:"        to "\uD83D\uDC4E",
        ":wave:"      to "\uD83D\uDC4B",
        ":ok:"        to "\uD83D\uDC4C",
        ":fire:"      to "\uD83D\uDD25",
        ":star:"      to "\u2B50",
        ":sun:"       to "\u2600\uFE0F",
        ":rain:"      to "\uD83C\uDF27\uFE0F",
        ":snow:"      to "\u2744\uFE0F",
        ":radio:"     to "\uD83D\uDCFB",
        ":antenna:"   to "\uD83D\uDCE1",
        ":sat:"       to "\uD83D\uDEF0\uFE0F",
        ":car:"       to "\uD83D\uDE97",
        ":boat:"      to "\u26F5",
        ":plane:"     to "\u2708\uFE0F",
        ":home:"      to "\uD83C\uDFE0",
        ":qrt:"       to "\uD83D\uDC4B",   // QRT = going off air → wave
        ":73:"        to "\uD83D\uDC4B",   // ham sign-off
        ":qsl:"       to "\u2705"          // confirmed
    )

    /**
     * Returns the input string with smiley/shortcode tokens replaced by
     * Unicode emoji. Each replacement requires the token to be preceded by
     * the start of the string or a whitespace character, to avoid matching
     * inside URLs (e.g. the ":)" in a tooltip-link suffix).
     */
    fun render(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for ((token, emoji) in MAP) {
            if (!out.contains(token)) continue
            val sb = StringBuilder(out.length + 8)
            var i = 0
            while (i < out.length) {
                if (i + token.length <= out.length &&
                    out.regionMatches(i, token, 0, token.length, ignoreCase = false)) {
                    val prev = if (i == 0) ' ' else out[i - 1]
                    if (prev == ' ' || prev == '\t' || prev == '\n' || prev == '\r') {
                        sb.append(emoji)
                        i += token.length
                        continue
                    }
                }
                sb.append(out[i])
                i++
            }
            out = sb.toString()
        }
        return out
    }
}