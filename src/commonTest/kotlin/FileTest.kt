import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTest {
    @Test
    fun testCwd() {
        val cwd = File(".")
        assertTrue(cwd.exists)
        assertTrue(cwd.isDirectory)
        assertFalse(cwd.isFile)
    }

    @Test
    fun testUserHome() {
        val userHome = File("~")
        assertEquals(userHome.path, USER_HOME)
        assertTrue(userHome.exists)
        assertTrue(userHome.isDirectory)
        assertFalse(userHome.isFile)
    }

    @Test
    fun testNonExistent() {
        val nonExistent = File("sir-not-appearing-in-this-file-system")
        assertFalse(nonExistent.exists)
        assertFalse(nonExistent.isDirectory)
        assertFalse(nonExistent.isFile)
    }

    // TODO: Test root dir ("/") and empty string ("").
}
