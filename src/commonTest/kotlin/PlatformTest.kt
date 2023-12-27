import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformTest {
    @Test
    fun testExecute() {
        val result = execute("echo hello")
        assertEquals(1, result?.size)
        assertEquals("hello", result?.get(0))
    }

    @Test
    fun testGetenv() {
        val path = getenv("PATH")
        assertNotNull(path)
        assertTrue(path.isNotEmpty())
    }
}
