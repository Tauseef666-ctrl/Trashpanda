package trashpanda.engine.execute

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.readText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Receipts are the app's only claim on what it moved — their format must round-trip losslessly. */
class ReceiptStoreTest {

    private val tempDir = Files.createTempDirectory("tp-receipt-test")
    private val store = ReceiptStore(tempDir)

    @AfterTest
    fun cleanup() { tempDir.toFile().deleteRecursively() }

    private fun sample() = listOf(
        ReceiptItem(
            originalPath = "C:\\Users\\test\\Temp\\evil<script>.txt",
            sizeBytes = 4_096,
            ruleId = "D1",
            ruleTitle = "User temp folder",
            verdict = "trash",
            risk = "green",
            status = Status.RECYCLED,
        ),
        ReceiptItem(
            originalPath = "C:\\locked\\profile with space\\thing",
            sizeBytes = 12,
            ruleId = "D9",
            ruleTitle = "Crash dumps",
            verdict = "trash",
            risk = "amber",
            status = Status.FAILED,
            error = "move refused by Windows (code 5)",
        ),
    )

    @Test
    fun `write then read is identical, Windows backslash paths and all`() {
        val written = store.write(sample())
        val readBack = store.read(tempDir.resolve("${written.id}.json"))
            ?: throw AssertionError("receipt file missing after write")
        assertEquals(written, readBack)
        assertTrue(readBack.items.first().originalPath.contains("script"), "escaping must survive")
    }

    @Test
    fun `saved json is human-readable`() {
        val receipt = store.write(sample())
        val text = tempDir.resolve("${receipt.id}.json").readText()
        assertTrue(text.contains("originalPath"), "attribution field present")
        assertTrue(text.trimStart().startsWith("{"))
        assertTrue(text.lines().size > 5, "pretty-printed, several lines")
    }

    @Test
    fun `list is newest first and latest picks the newest`() {
        val one = store.write(sample())
        Thread.sleep(1100) // receipt ids are second-stamped; keep ids distinct
        val two = store.write(sample())
        val listed = store.list()
        assertEquals(two.id, listed.first().id)
        assertEquals(one.id, listed[1].id)
        assertEquals(two.id, store.latest()!!.id)
    }

    @Test
    fun `state reflects its items`() {
        val recycledOnly = Receipt("id", Instant.now().toString(), 10, 0, sample().map { it.copy(status = Status.RECYCLED, error = null) })
        assertEquals("recycled", recycledOnly.state)
        val restored = recycledOnly.copy(items = recycledOnly.items.map { it.copy(status = Status.RESTORED, restoredTo = it.originalPath) })
        assertEquals("undone", restored.state)
    }
}
