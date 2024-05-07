plugins {
    val kotlinVersion = "1.9.21"
    kotlin("multiplatform") version kotlinVersion
}

group = "org.scijava"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    // Initialize all target platforms.
    // Incompatible targets will be automatically skipped with a warning;
    // we suppress such warnings by adding a line to gradle.properties:
    // kotlin.native.ignoreDisabledTargets=true
    val linuxArm64 = linuxArm64()
    val linuxX64 = linuxX64()
    val macosArm64 = macosArm64()
    val macosX64 = macosX64()
    val windows = mingwX64("windows")

    // Configure which native targets to build, based on current platform.
    val hostOs = System.getProperty("os.name")
    val nativeTargets = when {
        hostOs == "Linux" -> listOf(linuxArm64, linuxX64)
        hostOs == "Mac OS X" -> listOf(macosArm64, macosX64)
        hostOs.startsWith("Windows") -> listOf(windows)
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    // Define dependencies between source sets.
    // We declare an intermediate source set called posix, which
    // the Linux and macOS sources extend, but Windows does not.
    // For further details, see:
    // https://kotlinlang.org/docs/multiplatform-advanced-project-structure.html#declaring-custom-source-sets
    sourceSets {
        val posixMain by creating {
            dependsOn(commonMain.get())
        }
        val macosMain by creating {
          dependsOn(posixMain)
        }
        val linuxMain by creating {
          dependsOn(posixMain)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        val linuxArm64Main by getting {
            dependsOn(linuxMain)
        }
        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }
        // HACK: Prevent "Variable is never used" warnings.
        // Unfortunately, @Suppress("UNUSED_PARAMETER") does not do the trick.
        print("$macosArm64Main$macosX64Main$linuxArm64Main$linuxX64Main".substring(0, 0))
    }

    // Build the binaries for all activated native targets.
    nativeTargets.forEach {
        it.apply {
            binaries {
                executable {
                    entryPoint = "main"
                }
            }
        }
    }
}
