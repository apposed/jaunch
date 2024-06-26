import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests `jvm.kt` functions. */
class JvmTest {

    @Test
    fun testExtractJavaVersion() {
        val expected = mapOf(
            "jdk_adopt_11.0.8.10_x64" to "11.0.8.10",
            "jdk_adopt_14.0.2.12_x64" to "14.0.2.12",
            "jdk_adopt_8u265_x64" to "1.8.0_265",
            "jdk_adopt_11.0.7.10_x64" to "11.0.7.10",
            "jdk_adopt_14.0.1_x64" to "14.0.1",
            "jdk_adopt_8.0.252_x64" to "1.8.0.252",
            "jdk_adopt_11.0.7_x86" to "11.0.7",
            "jdk_adopt_14.0.1_x86" to "14.0.1",
            "jdk_adopt_8u252_x86" to "1.8.0_252",
            "jdk_corretto_11_x64" to "11",
            "jdk_corretto_8.x64" to "1.8",
            "jdk_corretto_11.0.7_x64" to "11.0.7",
            "jdk_corretto_8u252_x64" to "1.8.0_252",
            "jdk_corretto_8u252_x86" to "1.8.0_252",
            "Alibaba_Dragonwell_Extended_11.0.20.17.8_x64_linux" to "11.0.20.17.8",
            "Alibaba_Dragonwell_Extended_8.16.17_x64_linux" to "1.8.16.17",
            "graalvm-ce-java11-linux-amd64-22.3.0" to "11",
            "graalvm-ce-java17-linux-amd64-22.3.0" to "17",
            "graalvm-ce-java17-linux-amd64-22.3.2" to "17",
            "graalvm-jdk-17_linux-x64_bin" to "17",
            "jbrsdk-11_0_6-linux-x64-b520.66" to "11_0_6",
            "jbrsdk_11.0.8_x64" to "11.0.8",
            "jbrsdk-8u232-linux-x64-b1638.6" to "1.8.0_232",
            "jbrsdk-8u242-linux-x64-b1644.3" to "1.8.0_242",
            "jbrsdk_8u252_x64" to "1.8.0_252",
            "jbrsdk-8u232-linux-i586-b1638.6" to "1.8.0_232",
            "jbrsdk-8u242-linux-i586-b1644.3" to "1.8.0_242",
            "jbrsdk-11_0_6-windows-x64-b520.66" to "11_0_6",
            "jbrsdk_11.0.7_x64" to "11.0.7",
            "jbrsdk-8u232-windows-x64-b1638.6" to "1.8.0_232",
            "jbrsdk-8u242-windows-x64-b1644.3" to "1.8.0_242",
            "jbrsdk_8u252_x64" to "1.8.0_252",
            "jbrsdk_11.0.7_x86" to "11.0.7",
            "jbrsdk-8u232-windows-i586-b1638.6" to "1.8.0_232",
            "jbrsdk-8u242-windows-i586-b1644.3" to "1.8.0_242",
            "jbrsdk_8u252_x86" to "1.8.0_252",
            "openjdk-21.0.1_linux-aarch64_bin" to "21.0.1",
            "openjdk-21.0.1_linux-x64_bin" to "21.0.1",
            "openjdk-21.0.1_macos-aarch64_bin" to "21.0.1",
            "openjdk-21.0.1_macos-x64_bin" to "21.0.1",
            "openjdk-21.0.1_windows-x64_bin" to "21.0.1",
            "TencentKona-11.0.21.b1-jdk_linux-x86_64" to "11.0.21",
            "TencentKona-17.0.9.b1-jdk_linux-x86_64" to "17.0.9",
            "TencentKona8.0.16.b1_jdk_linux-x86_64_8u392" to "1.8.0_392",
            "bellsoft-jdk21.0.1+12-linux-amd64" to "21.0.1+12",
            "microsoft-jdk-21.0.1-linux-aarch64" to "21.0.1",
            "microsoft-jdk-11.0.21-linux-x64" to "11.0.21",
            "microsoft-jdk-17.0.9-linux-x64" to "17.0.9",
            "microsoft-jdk-21.0.1-linux-x64" to "21.0.1",
            "microsoft-jdk-21.0.1-macos-aarch64" to "21.0.1",
            "microsoft-jdk-21.0.1-macos-x64" to "21.0.1",
            "openlogic-openjdk-17.0.9+9-linux-x64" to "17.0.9+9",
            "jdk-21_linux-aarch64_bin" to "21",
            "jdk-21_linux-x64_bin" to "21",
            "jdk_oracle_11.0.8_x64" to "11.0.8",
            "jdk_oracle_14.0.2_x64" to "14.0.2",
            "jdk_oracle_8u261_x64" to "1.8.0_261",
            "jdk-1_2_2_017-linux-i586" to "1_2_2_017",
            "jdk-21_macos-aarch64_bin" to "21",
            "jdk-21_macos-x64_bin" to "21",
            "jdk-21_windows-x64_bin" to "21",
            "jdk_oracle_11.0.7_x64" to "11.0.7",
            "jdk_oracle_14_x64" to "14",
            "jdk_oracle_8u251_x64" to "1.8.0_251",
            "jdk_oracle_8u251_x86" to "1.8.0_251",
            "sapmachine-jdk-11.0.21_linux-x64_bin" to "11.0.21",
            "sapmachine-jdk-17.0.9_linux-x64_bin" to "17.0.9",
            "sapmachine-jdk-21.0.1_linux-x64_bin" to "21.0.1",
            "ibm-semeru-certified-jdk_x64_linux_17.0.9.0" to "17.0.9.0",
            "ibm-semeru-certified-jre_x64_linux_17.0.9.0" to "17.0.9.0",
            "ibm-semeru-open-jdk_x64_linux_17.0.9_9_openj9-0.41.0" to "17.0.9_9",
            "ibm-semeru-open-jdk_x64_linux_20.0.2_9_openj9-0.40.0" to "20.0.2_9",
            "ibm-semeru-open-jre_x64_linux_17.0.9_9_openj9-0.41.0" to "17.0.9_9",
            "jdk_zulu_11_x64" to "11",
            "jdk_zulu_14_x64" to "14",
            "jdk_zulu_8_x64" to "1.8",
            "zulu11.39.15-ca-jdk11.0.7-linux_musl_x64" to "11.0.7",
            "zulu11.39.15-ca-jdk11.0.7-linux_x64" to "11.0.7",
            "zulu11.60.19-ca-fx-jdk11.0.17-linux_x64" to "11.0.17",
            "zulu11.66.15-ca-jdk11.0.20-linux_x64" to "11.0.20",
            "zulu13.31.11-ca-jdk13.0.3-linux_x64" to "13.0.3",
            "zulu14.28.21-ca-jdk14.0.1-linux_x64" to "14.0.1",
            "zulu20.32.11-ca-jdk20.0.2-linux_x64" to "20.0.2",
            "zulu8.46.0.19-ca-fx-jdk8.0.252-linux_x64" to "1.8.0.252",
            "zulu8.70.0.23-ca-fx-jdk8.0.372-linux_x64" to "1.8.0.372",
            "jdk_zulu_11_x64" to "11",
            "jdk_zulu_14_x64" to "14",
            "jdk_zulu_8_x64" to "1.8",
            "zulu11.39.15-ca-jdk11.0.7-win_x64" to "11.0.7",
            "zulu13.31.11-ca-jdk13.0.3-win_x64" to "13.0.3",
            "zulu14.28.21-ca-jdk14.0.1-win_x64" to "14.0.1",
            "zulu8.46.0.19-ca-fx-jdk8.0.252-win_x64" to "1.8.0.252",
            "jdk_zulu_11_x86" to "11",
            "jdk_zulu_14_x86" to "14",
            "jdk_zulu_8_x86" to "1.8",
        )
        expected.forEach {
            assertEquals(it.value, extractJavaVersion(it.key), it.key)
        }
    }

    @Test
    fun testCompareVersions() {
        assertEquals(0, compareVersions("1.8.0_255", "1.8"))
        assertEquals(0, compareVersions("1.8.0_255", "8"))
        assertTrue(compareVersions("1.8.0_255", "21") < 0)
        assertTrue(compareVersions("1.8.0_255", "1.6") > 0)
        assertTrue(compareVersions("1.8.0_255", "6") > 0)
    }

    @Test
    fun testVersionOutOfBounds() {
        // Test in-bounds versions.
        assertFalse(versionOutOfBounds("1.8.0_255", "8", "21"))
        assertFalse(versionOutOfBounds("1.8.0_255", "8", null))
        assertFalse(versionOutOfBounds("1.8.0_255", "1.6", "8"))
        // Test out-of-bounds versions.
        assertTrue(versionOutOfBounds("1.8.0_255", "11", "21"))
        assertTrue(versionOutOfBounds("1.8.0_255", "11", null))
        assertTrue(versionOutOfBounds("1.8.0_92", "1.8.0_101", null))
        assertTrue(versionOutOfBounds("1.8.0_255", null, "1.6"))
        assertTrue(versionOutOfBounds("1.8.0_255", null, "6"))
    }
}
