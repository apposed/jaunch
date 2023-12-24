import kotlin.test.Test
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
        assertTrue(userHome.exists)
        assertTrue(userHome.isDirectory)
        assertFalse(userHome.isFile)
    }

    @Test
    fun testNonExistent() {
        val nonExistent = File("sir-not-appearing-in-this-film")
        assertFalse(nonExistent.exists)
        assertFalse(nonExistent.isDirectory)
        assertFalse(nonExistent.isFile)
    }
}
