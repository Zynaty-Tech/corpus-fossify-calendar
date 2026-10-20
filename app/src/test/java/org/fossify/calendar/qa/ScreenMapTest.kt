package org.fossify.calendar.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The mqa screen map and the code must agree: every tagged destination (activity or calendar view) is in the map,
 * and every map entry (destinations and dialogs) names a declared screen tag id.
 */
class ScreenMapTest {
    private val map = File("src/qa/assets/mqa-screens.json").readText()
    private val inMap = Regex(""""tag":\s*"(\w+)"""").findAll(map).map { it.groupValues[1] }.toSortedSet()

    @Test
    fun screenMapListsEveryTaggedDestination() {
        val tagger = File("src/main/kotlin/org/fossify/calendar/qa/ScreenTags.kt").readText()
        val destinations = tagger.substringAfter("val ACTIVITIES").substringBefore("val DIALOG_FRAGMENTS")
        val inCode = Regex("""::class\.java to R\.id\.screen_tag_(\w+)""").findAll(destinations).map { it.groupValues[1] }
        assertEquals(emptySet<String>(), inCode.toSortedSet() - inMap)
    }

    @Test
    fun everyMapEntryIsADeclaredTag() {
        val ids = File("src/main/res/values/mqa_screen_tags.xml").readText()
        val declared = Regex("""name="screen_tag_(\w+)"""").findAll(ids).map { it.groupValues[1] }.toSet()
        val unknown = inMap - declared
        assertTrue("map tags with no screen_tag_ id: $unknown", unknown.isEmpty())
    }
}
