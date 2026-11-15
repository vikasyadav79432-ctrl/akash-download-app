package com.example.domain.parser

object IsoDurationParser {

    private val ISO_DURATION_REGEX = Regex("^PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?$")

    fun formatDuration(isoDuration: String?): String? {
        if (isoDuration.isNullOrBlank()) return null
        val match = ISO_DURATION_REGEX.matchEntire(isoDuration.trim()) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    fun parseTotalSeconds(isoDuration: String?): Int? {
        if (isoDuration.isNullOrBlank()) return null
        val match = ISO_DURATION_REGEX.matchEntire(isoDuration.trim()) ?: return null
        val hours = match.groupValues[1].toIntOrNull() ?: 0
        val minutes = match.groupValues[2].toIntOrNull() ?: 0
        val seconds = match.groupValues[3].toIntOrNull() ?: 0
        return hours * 3600 + minutes * 60 + seconds
    }
}
