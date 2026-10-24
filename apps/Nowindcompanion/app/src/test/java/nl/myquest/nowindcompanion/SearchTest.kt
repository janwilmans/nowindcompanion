package nl.myquest.nowindcompanion

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTest {
    private val entries = listOf(
        "Nemesis 2 (1987)(Konami).dsk",
        "Aleste 2 (1988)(Compile).dsk",
        "Knightmare (1986)(Konami).dsk",
        "Nemesis (1986)(Konami).dsk",
        "Aleste (1988)(Compile).dsk",
        "Penguin Adventure (1986)(Konami).dsk",
    ).map { RemoteEntry(it, "https://example.com/$it", isDirectory = false) }

    private fun search(query: String) = searchEntries(entries, query).map { it.name }

    @Test
    fun entries_with_all_keywords_first_then_the_first_keyword_then_the_next() {
        assertEquals(
            listOf(
                "Nemesis (1986)(Konami).dsk",               // both keywords
                "Nemesis 2 (1987)(Konami).dsk",             // only 'nemesis'
                "Knightmare (1986)(Konami).dsk",            // only '1986'
                "Penguin Adventure (1986)(Konami).dsk",
            ),
            search("nemesis 1986")
        )
    }

    @Test
    fun the_order_of_the_keywords_decides_which_partial_hits_come_first() {
        assertEquals(
            listOf(
                "Nemesis (1986)(Konami).dsk",
                "Knightmare (1986)(Konami).dsk",
                "Penguin Adventure (1986)(Konami).dsk",
                "Nemesis 2 (1987)(Konami).dsk",
            ),
            search("1986 nemesis")
        )
    }

    @Test
    fun one_keyword_is_alphabetical_and_case_insensitive() {
        assertEquals(listOf("Aleste (1988)(Compile).dsk", "Aleste 2 (1988)(Compile).dsk"), search("  ALESTE "))
    }

    @Test
    fun three_keywords_group_by_the_first_one_found() {
        assertEquals(
            listOf(
                "Aleste 2 (1988)(Compile).dsk",             // all three
                "Nemesis 2 (1987)(Konami).dsk",             // '2'
                "Aleste (1988)(Compile).dsk",               // 'aleste', not '2'
            ),
            search("2 aleste compile")
        )
    }

    @Test
    fun an_empty_query_keeps_the_listing_as_it_is() {
        assertEquals(entries.map { it.name }, search("   "))
    }

    @Test
    fun entries_without_any_keyword_are_left_out() {
        assertEquals(emptyList<String>(), search("gradius"))
    }
}
