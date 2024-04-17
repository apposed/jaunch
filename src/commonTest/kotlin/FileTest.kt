import kotlin.test.*

/** Tests `File.kt` behavior. */
class FileTest {
    @Test
    fun testBasics() {
        val file = File("path") / "to" / "hello.txt"
        assertEquals("hello.txt", file.name)
        assertEquals("txt", file.suffix)
        assertTrue(file.base.path.endsWith("${SLASH}path${SLASH}to${SLASH}hello"))
        assertTrue(file.dir.path.endsWith("${SLASH}path${SLASH}to"))
    }

    @Test
    fun testExists() {
        assertTrue(File(".").exists)
        assertFalse(File("this-file-is-unlikely-to-exist").exists)
    }

    @Test
    fun testCwd() {
        for (p in listOf(".", "")) {
            val cwd = File(p)
            assertTrue(cwd.exists)
            assertTrue(cwd.isDirectory)
            assertFalse(cwd.isFile)
            assertFalse(cwd.isRoot) // Won't happen in practice.
        }
    }

    @Test
    fun testUserHome() {
        val userHome = File("~")
        assertEquals(USER_HOME, userHome.path)
        assertTrue(userHome.exists)
        assertTrue(userHome.isDirectory)
        assertFalse(userHome.isFile)
        assertFalse(userHome.isRoot)
    }

    @Test
    fun testNonExistent() {
        val nonExistent = File("sir-not-appearing-in-this-file-system")
        assertFalse(nonExistent.exists)
        assertFalse(nonExistent.isDirectory)
        assertFalse(nonExistent.isFile)
        assertFalse(nonExistent.isRoot)
    }

    @Test
    fun testRootDir() {
        val rootDir = File(SLASH)
        assertTrue(rootDir.exists)
        assertFalse(rootDir.isFile)
        assertTrue(rootDir.isDirectory)
        assertTrue(rootDir.isRoot)
        assertFailsWith<IllegalArgumentException>("Root directory has no parent") { rootDir.dir }
    }
}
