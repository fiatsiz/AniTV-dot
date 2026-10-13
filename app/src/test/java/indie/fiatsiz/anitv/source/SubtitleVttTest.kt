package indie.fiatsiz.anitv.source

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleVttTest {
    @Test
    fun parsesTimedCuesAndKeepsCueText() {
        val cues = HiAnimeSource.parseVtt("""WEBVTT

1
00:00:01.250 --> 00:00:03.000 align:start
<i>Hello</i>
there

00:01.500 --> 00:02.250
Second cue
""")

        assertEquals(listOf(SubtitleCue(1_250, 3_000, "Hello there"), SubtitleCue(1_500, 2_250, "Second cue")), cues)
    }

    @Test
    fun ignoresMalformedAndEmptyCues() {
        val cues = HiAnimeSource.parseVtt("""WEBVTT

not a cue
Hello

00:00:03.000 --> 00:00:01.000
Backwards

00:00:04.000 --> 00:00:05.000
<b></b>
""")

        assertEquals(emptyList<SubtitleCue>(), cues)
    }
}
