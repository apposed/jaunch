import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Config (
    val launch: Launch,
    @SerialName("java-search")
    val javaSearch: JavaSearch,
)

/** The launch section configures how Jaunch will launch the JVM. */
@Serializable
data class Launch (

    /** Path to splash screen image to show, if desired. */
    @SerialName("splash-image")
    val splashImage: String?,

    /** Runtime classpath elements to pass to Java. */
    val classpath: Array<String>,

    /**
     * Maximum amount of memory for the Java heap to consume. Examples:
     * <ul>
     * <li>For 1567 MB: "1567m"</li>
     * <li>For 48 GB: "48g"</li>
     * <li>For 75% of available RAM: "75%"</li>
     * <li>For 3 GB less than available RAM: "-3g"</li>
     * </ul>
     * These will be translated into an appropriate "-Xmx..." argument.
     */
    @SerialName("max-heap")
    val maxHeap: String? = "75%",

    /** Additional flags to pass to the JVM at launch. */
    val flags: Array<String>,
)

/** The java-search section defines where Jaunch will discover Java installations. */
@Serializable
data class JavaSearch (

    /** Minimum acceptable Java version to match. */
    @SerialName("version-min")
    val versionMin: Long? = null,

    /** Maximum acceptable Java version to match. */
    @SerialName("version-max")
    val versionMax: Long? = null,

    /** Paths to check on all systems. */
    @SerialName("root-paths")
    val rootPaths: Array<String> = emptyArray(),

    /**
     * Any of the following can be bare, or nested in another JDK root folder.
     * No assumption is made about the naming scheme of such a JDK root folder,
     * because not all distros are predictably named. Examples:
     * <pre>
     * macosx  JBRSDK 8u252          : jdk/Contents/Home/jre/lib/jli/libjli.dylib
     * linux64 JBRSDK 11.0.6-b520.66 : jbrsdk/lib/server/libjvm.so
     * linux64 JBRSDK 11.0.8         : jbrsdk_11.0.8_x64/jbr/lib/server/libjvm.so
     * </pre>
     * The nested jre/ is only present for OpenJDK 8, nothing after.
     * But one wrinkle: on macOS, some distros *also* have a libjli.dylib
     * in Contents/MacOS in addition to its other location. And the two
     * are *not* binary identical. Testing is needed to determine if
     * there's any difference in behavior based on which one gets linked.
     */
    @SerialName("libjvm-suffixes")
    val libjvmSuffixes: Array<String> = emptyArray(),
)
