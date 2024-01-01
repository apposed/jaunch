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

    @Test
    fun testMemInfo() {
        val totalMem = memInfo().total
        assertNotNull(totalMem)
        assertTrue(totalMem > 256 * 1024 * 1024)
    }

    @Test
    fun testConstants() {
        assertTrue(
            (OS_NAME == "WINDOWS" && SLASH == "\\" && COLON == ";" && NL == "\r\n") ||
            (OS_NAME == "MACOSX" && SLASH == "/" && COLON == ":" && NL == "\n") ||
            (OS_NAME == "LINUX" && SLASH == "/" && COLON == ":" && NL == "\n")
        )
    }
}
