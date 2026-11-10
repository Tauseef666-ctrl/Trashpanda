@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package trashpanda.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "never touch" screen at the widget layer: duplicates refused (keyed match, not
 * raw text), a fresh path reaches state verbatim, and a row's remove hands back the
 * exact stored entry. State lives in Main; the test holds it the same way.
 */
class ExclusionsScreenTest {

    @Test
    fun `duplicate refused, fresh path added, remove hands the stored entry back`() = runComposeUiTest {
        val added = mutableListOf<String>()
        val removed = mutableListOf<String>()

        setContent {
            var exclusions by remember { mutableStateOf(listOf("D:\\My Games")) }
            ExclusionsScreen(
                exclusions = exclusions,
                onAdd = { added += it; exclusions = exclusions + it },
                onRemove = { removed += it; exclusions = exclusions - it },
                onBack = {},
            )
        }

        // Seeded row exists.
        onNodeWithText("D:\\My Games").assertExists()
        assertEquals(0, added.size, "sanity: nothing added yet")

        // Typing a duplicate must be refused: still only the row (+ the field's own text).
        onNodeWithTag("exclusion-input").performTextInput("D:\\My Games")
        onNodeWithText("Add").performClick()
        assertEquals(0, added.size, "a duplicate exclusion must be refused")
        onAllNodesWithText("D:\\My Games").assertCountEquals(2) // one row + the field draft

        // A fresh path adds verbatim.
        onNodeWithTag("exclusion-input").performTextClearance()
        onNodeWithTag("exclusion-input").performTextInput("C:\\precious")
        onNodeWithText("Add").performClick()
        assertEquals(listOf("C:\\precious"), added, "the new path must reach state verbatim")

        // Its row's remove hands back the exact stored entry.
        onNodeWithText("C:\\precious").assertExists()
        onNodeWithTag("remove: C:\\precious").performClick()
        assertEquals(listOf("C:\\precious"), removed)
        onNodeWithText("C:\\precious").assertDoesNotExist()
    }
}
