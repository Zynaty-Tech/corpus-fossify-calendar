package org.fossify.calendar.qa

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Fails when a tagged screen (activity or calendar view) is missing from the mqa screen map, or the reverse. */
class ScreenMapTest {
    @Test
    fun screenMapListsEveryTaggedDestination() {
        val tagger = File("src/main/kotlin/org/fossify/calendar/qa/ScreenTags.kt").readText()
        // Destinations only: the ACTIVITIES and FRAGMENTS maps. Dialogs are not listed in the map.
        val destinations = tagger.substringAfter("val ACTIVITIES").substringBefore("val DIALOG_FRAGMENTS")
        val inCode = Regex("""::class\.java to R\.id\.screen_tag_(\w+)""").findAll(destinations).map { it.groupValues[1] }
        val map = File("src/qa/assets/mqa-screens.json").readText()
        val inMap = Regex(""""tag":\s*"(\w+)"""").findAll(map).map { it.groupValues[1] }
        assertEquals(inCode.toSortedSet(), inMap.toSortedSet())
    }
}
