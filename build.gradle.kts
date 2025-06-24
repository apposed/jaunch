plugins {
    val kotlinVersion = "1.9.21"
    kotlin("multiplatform") version kotlinVersion
}

group = "org.scijava"
version = "1.0.4"

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
    // Note: Kotlin Native doesn't support Windows arm64 yet:
    //   https://youtrack.jetbrains.com/issue/KT-68504
    //val windowsArm64 = mingwArm64("windowsArm64")
    val windowsX64 = mingwX64("windowsX64")

    // Configure which native targets to build, based on current platform.
    val hostOs = System.getProperty("os.name")
    val nativeTargets = when {
        hostOs == "Linux" -> listOf(linuxArm64, linuxX64)
        hostOs == "Mac OS X" -> listOf(macosArm64, macosX64)
        hostOs.startsWith("Windows") -> listOf(/*windowsArm64, */windowsX64)
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
        val linuxMain by creating {
          dependsOn(posixMain)
        }
        val macosMain by creating {
          dependsOn(posixMain)
        }
        val windowsMain by creating {
          dependsOn(commonMain.get())
        }
        val linuxArm64Main by getting {
            dependsOn(linuxMain)
        }
        val linuxX64Main by getting {
            dependsOn(linuxMain)
        }
        val macosArm64Main by getting {
            dependsOn(macosMain)
        }
        val macosX64Main by getting {
            dependsOn(macosMain)
        }
        //val windowsArm64Main by getting {
        //    dependsOn(windowsMain)
        //}
        val windowsX64Main by getting {
            dependsOn(windowsMain)
        }
        // HACK: Prevent "Variable is never used" warnings.
        // Unfortunately, @Suppress("UNUSED_PARAMETER") does not do the trick.
        print((
          "  $linuxArm64Main$linuxX64Main" +
          "  $macosArm64Main$macosX64Main" +
          " windowsArm64Main$windowsX64Main"
        ).substring(0, 0))
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

    // NB: Tell the GNU linker to only link to shared libraries as needed.
    // We do this in particular to avoid libcrypt being linked into the
    // executable somehow; this matters for certain distros (e.g. Manjaro
    // Linux) that do not come with libcrypt out of the box.
    linuxX64 {
        binaries {
            all {
                linkerOpts += "-Wl,--as-needed"
            }
        }
    }
    linuxArm64 {
        binaries {
            all {
                linkerOpts += "-Wl,--as-needed"
            }
        }
    }
}
