package com.signalout.android.ui

import androidx.compose.ui.text.AnnotatedString
import android.util.Patterns

import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.graphics.Color

/**
 * Utilities for parsing special tokens in chat messages (geohashes, etc.).
 */
object MessageSpecialParser {
    // Standalone geohash pattern like "#9q" or longer. Word boundaries enforced.
    // Geohash alphabet is base32: 0123456789bcdefghjkmnpqrstuvwxyz
    private val standaloneGeohashRegex = Regex("(^|[^A-Za-z0-9_#])#([0-9bcdefghjkmnpqrstuvwxyz]{2,})($|[^A-Za-z0-9_])", RegexOption.IGNORE_CASE)

    data class GeohashMatch(val start: Int, val endExclusive: Int, val geohash: String)
    data class UrlMatch(val start: Int, val endExclusive: Int, val url: String)

    /**
     * Finds standalone geohashes within [text]. A match is returned only when
     * the '#' token is not part of another word (e.g., not in '@anon#9qk2').
     */
    fun findStandaloneGeohashes(text: String): List<GeohashMatch> {
        if (text.isEmpty()) return emptyList()
        val matches = mutableListOf<GeohashMatch>()
        var index = 0
        while (index < text.length) {
            val m = standaloneGeohashRegex.find(text, index) ?: break
            // Adjust to only cover the geohash token starting at '#'
            val fullRange = m.range
            // Find the '#' within this match substring
            val sub = text.substring(fullRange)
            val hashPos = sub.indexOf('#')
            if (hashPos >= 0) {
                val tokenStart = fullRange.first + hashPos
                // Consume '#' + geohash letters
                var cursor = tokenStart + 1
                while (cursor < text.length) {
                    val ch = text[cursor].lowercaseChar()
                    val isGeoChar = (ch in '0'..'9') || (ch in "bcdefghjkmnpqrstuvwxyz")
                    if (!isGeoChar) break
                    cursor++
                }
                val token = text.substring(tokenStart + 1, cursor)
                if (token.length >= 2) {
                    matches.add(GeohashMatch(tokenStart, cursor, token.lowercase()))
                }
                index = cursor
            } else {
                index = fullRange.last + 1
            }
        }
        return matches
    }

    /**
     * Detect URLs in text. Supports http(s) schemes, www.* domains, and bare domains with TLDs.
     * Trailing punctuation like '.', ',', ')', '!' is trimmed from the match.
     */
    fun findUrls(text: String): List<UrlMatch> {
        if (text.isEmpty()) return emptyList()
        val results = mutableListOf<UrlMatch>()

        // 1) Use Android's WEB_URL for robust detection of http(s) and www.*
        val webUrl = Patterns.WEB_URL
        val matcher = webUrl.matcher(text)
        while (matcher.find()) {
            var start = matcher.start()
            var endExclusive = matcher.end()
            var token = text.substring(start, endExclusive)

            // Trim trailing punctuation
            while (token.isNotEmpty() && token.last() in setOf('.', ',', ';', ':', '!', '?', '\'', '"')) {
                endExclusive -= 1
                token = text.substring(start, endExclusive)
            }
            results.add(UrlMatch(start, endExclusive, token))
        }

        // 2) Bare-domain fallback for things WEB_URL can miss (e.g., google.com)
        val bare = Regex("(?<!@)(?<![A-Za-z0-9_-])([A-Za-z0-9-]+\\.[A-Za-z]{2,}(?:/[A-Za-z0-9@:%._+~#=/?&!$'()*,-]*)?)")
        for (m in bare.findAll(text)) {
            val start = m.range.first
            var endExclusive = m.range.last + 1
            var token = text.substring(start, endExclusive)

            // Exclude if overlaps any already-found match
            val overlapsExisting = results.any { start < it.endExclusive && endExclusive > it.start }
            if (overlapsExisting) continue

            // Skip emails
            if (token.contains('@')) continue

            // Trim trailing punctuation
            while (token.isNotEmpty() && token.last() in setOf('.', ',', ';', ':', '!', '?', '\'', '"')) {
                endExclusive -= 1
                token = text.substring(start, endExclusive)
            }

            // Require at least one dot
            if (!token.contains('.')) continue

            results.add(UrlMatch(start, endExclusive, token))
        }

        // Sort by start index
        results.sortBy { it.start }
        return results
    }

    /**
     * Parses WhatsApp-style Markdown:
     * *bold*, _italics_, - list point, > blockquote
     */
    fun parseMarkdown(text: String, isDark: Boolean = true): AnnotatedString {
        return buildAnnotatedString {
            val lines = text.split("\n")
            for ((index, line) in lines.withIndex()) {
                var processedLine = line

                // Handle > blockquotes
                val isBlockquote = processedLine.trimStart().startsWith("> ")
                if (isBlockquote) {
                    processedLine = processedLine.replaceFirst("> ", "")
                    withStyle(
                        style = SpanStyle(
                            background = if (isDark) Color(0xFF2A3942) else Color(0xFFF0F2F5),
                            color = if (isDark) Color(0xFF8696A0) else Color(0xFF667781),
                            fontStyle = FontStyle.Italic
                        )
                    ) {
                        append("┃ ")
                        parseInlineMarkdown(processedLine)
                    }
                } else {
                    // Handle - list points
                    val isPoint = processedLine.trimStart().startsWith("- ")
                    if (isPoint) {
                        processedLine = processedLine.replaceFirst("- ", "• ")
                    }
                    parseInlineMarkdown(processedLine)
                }

                if (index < lines.size - 1) {
                    append("\n")
                }
            }
        }
    }

    private fun AnnotatedString.Builder.parseInlineMarkdown(line: String) {
        var currentIndex = 0
        // Combined regex for *bold* and _italics_
        val regex = Regex("(\\*([^*]+)\\*)|(_([^_]+)_)")
        val matches = regex.findAll(line)
        
        for (match in matches) {
            // Append text before the match
            if (currentIndex < match.range.first) {
                append(line.substring(currentIndex, match.range.first))
            }
            
            val fullMatch = match.value
            if (fullMatch.startsWith("*") && fullMatch.endsWith("*")) {
                withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[2])
                }
            } else if (fullMatch.startsWith("_") && fullMatch.endsWith("_")) {
                withStyle(style = SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(match.groupValues[4])
                }
            }
            
            currentIndex = match.range.last + 1
        }
        
        // Append remaining text
        if (currentIndex < line.length) {
            append(line.substring(currentIndex))
        }
    }
}


