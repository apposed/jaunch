plugins {
    kotlin("multiplatform") version "1.9.21"
}

group = "org.scijava"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val v = mapOf(
    "okio" to "3.6.0",
    "ktoml" to "0.5.0",
)

kotlin {
    val hostOs = System.getProperty("os.name")
    val isArm64 = System.getProperty("os.arch") == "aarch64"
    val isMingwX64 = hostOs.startsWith("Windows")
    val nativeTarget = when {
        hostOs == "Mac OS X" && isArm64 -> macosArm64("posix")
        hostOs == "Mac OS X" && !isArm64 -> macosX64("posix")
        hostOs == "Linux" && isArm64 -> linuxArm64("posix")
        hostOs == "Linux" && !isArm64 -> linuxX64("posix")
        isMingwX64 -> mingwX64("windows")
        else -> throw GradleException("Host OS is not supported in Kotlin/Native.")
    }

    nativeTarget.apply {
        binaries {
            executable {
                entryPoint = "main"
            }
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("com.squareup.okio:okio:${v["okio"]}")
                implementation("com.akuleshov7:ktoml-core:${v["ktoml"]}")
                implementation("com.akuleshov7:ktoml-file:${v["ktoml"]}")
            }
        }
        val commonTest by getting
    }
}
