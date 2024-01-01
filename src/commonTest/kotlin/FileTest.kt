import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FileTest {
    @Test
    fun testBasics() {
        val file = File("path${SLASH}to${SLASH}hello.txt")
        assertEquals("hello.txt", file.name)
        assertEquals("txt", file.suffix)
        assertEquals("path${SLASH}to${SLASH}hello", file.withoutSuffix)
        assertEquals("path${SLASH}to", file.directoryPath)
    }

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
