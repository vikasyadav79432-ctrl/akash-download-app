package com.example

import com.example.domain.model.ParsedUrlResult
import com.example.domain.parser.IsoDurationParser
import com.example.domain.parser.VideoUrlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoUrlParserTest {

    @Test
    fun `parses standard youtube watch url`() {
        val result = VideoUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=12s")
        assertTrue(result is ParsedUrlResult.ValidYouTube)
        val yt = result as ParsedUrlResult.ValidYouTube
        assertEquals("dQw4w9WgXcQ", yt.videoId)
        assertFalse(yt.isShort)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", yt.normalizedUrl)
    }

    @Test
    fun `parses youtu be shortlink`() {
        val result = VideoUrlParser.parse("https://youtu.be/jNQXAC9IVRw?si=abc123")
        assertTrue(result is ParsedUrlResult.ValidYouTube)
        val yt = result as ParsedUrlResult.ValidYouTube
        assertEquals("jNQXAC9IVRw", yt.videoId)
        assertFalse(yt.isShort)
    }

    @Test
    fun `identifies youtube shorts url`() {
        val result = VideoUrlParser.parse("https://youtube.com/shorts/aqz-KE-bpKQ?feature=share")
        assertTrue(result is ParsedUrlResult.ValidYouTube)
        val yt = result as ParsedUrlResult.ValidYouTube
        assertEquals("aqz-KE-bpKQ", yt.videoId)
        assertTrue(yt.isShort)
    }

    @Test
    fun `extracts url from youtube share text`() {
        val result = VideoUrlParser.parse("Watch this clip https://www.youtube.com/shorts/aqz-KE-bpKQ")
        assertTrue(result is ParsedUrlResult.ValidYouTube)
        val yt = result as ParsedUrlResult.ValidYouTube
        assertEquals("aqz-KE-bpKQ", yt.videoId)
        assertTrue(yt.isShort)
    }

    @Test
    fun `rejects playlist url without video id`() {
        val result = VideoUrlParser.parse("https://www.youtube.com/playlist?list=PL1234567890")
        assertTrue(result is ParsedUrlResult.Invalid)
    }

    @Test
    fun `rejects malformed youtube video id`() {
        val result = VideoUrlParser.parse("https://www.youtube.com/watch?v=too_short")
        assertTrue(result is ParsedUrlResult.Invalid)
    }

    @Test
    fun `rejects insecure http direct download url`() {
        val result = VideoUrlParser.parse("http://example.com/video.mp4")
        assertTrue(result is ParsedUrlResult.Invalid)
    }

    @Test
    fun `parses https direct mp4 url`() {
        val result = VideoUrlParser.parse("https://cdn.example.com/media/sample_video.mp4")
        assertTrue(result is ParsedUrlResult.ValidDirectMedia)
        val direct = result as ParsedUrlResult.ValidDirectMedia
        assertEquals("sample_video.mp4", direct.suggestedFileName)
        assertFalse(direct.isManifestHint)
    }

    @Test
    fun `formats iso durations accurately`() {
        assertEquals("4:13", IsoDurationParser.formatDuration("PT4M13S"))
        assertEquals("1:02:05", IsoDurationParser.formatDuration("PT1H2M5S"))
        assertEquals("0:45", IsoDurationParser.formatDuration("PT45S"))
        assertEquals(null, IsoDurationParser.formatDuration("invalid"))
    }
}
