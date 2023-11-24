@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

import jni.*
import kotlinx.cinterop.*
import kotlin.experimental.ExperimentalNativeApi
import platform.posix.*

@OptIn(ExperimentalNativeApi::class)
fun main(vararg args: String) {
    require(args.size == 3) { "Needs classpath + 2 parameters " }
    val classPath = "-Djava.class.path=${args[0]}"
    val linkingHello = args[1]
    val linkingNumber = requireNotNull(args[2].toIntOrNull())

    /*
    // Load libjvm dynamically
    val jvmLibName = when (Platform.osFamily) {
        OsFamily.WINDOWS -> "jvm.dll"
        OsFamily.MACOSX -> "libjli.so"
        OsFamily.LINUX -> "libjvm.so"
        else -> error("Unsupported operating system")
    }

    val jvmLib = dlopen(jvmLibName, RTLD_LAZY or RTLD_GLOBAL)
    if (jvmLib == null) {
        println("Error loading $jvmLibName: ${dlerror()?.toKString()}")
        return
    }
    */

    println(classPath)
    val jvmArena = Arena()
    val jvm = jvmArena.alloc<CPointerVar<JavaVMVar>>()
    try {
        memScoped {
            val vmArgs = alloc<JavaVMInitArgs>()
            vmArgs.version = JNI_VERSION_10
            vmArgs.nOptions = 1
            val options = allocArray<JavaVMOption>(vmArgs.nOptions)
            options[0].optionString = classPath.cstr.ptr

            vmArgs.options = options
            val penv = alloc<CPointerVar<JNIEnvVar>>()

            println("CREATE JVM")
            val resultCreateJvm = JNI_CreateJavaVM(jvm.ptr, penv.ptr.reinterpret(), vmArgs.ptr)
            println("CHECK JVM")
            require(resultCreateJvm == JNI_OK) {
                "JNI_CreateJavaVM failed"
            }
            val env = penv.value!!
            println("JVM CREATED")
            val version = requireNotNull(env.pointed.pointed) {
                "VERSION env.pointed.pointed was null"
            }.let { requireNotNull(it.GetVersion) { "VERSION it.GetVersion was null " } }(env)
            println("GOT VERSION $version")

            val jcls = env.findClass("sample/MainKt")
            println("GOT MainKt")
            val jclEntry = env.getStaticMethod(jcls, "cobolEntry", "(Lsample/Linking;)V")
            println("GOT jclEntry")
            val optionsClass = env.findClass("sample/Linking")
            println("GOT optionsClass")
            val init = env.getMethod(optionsClass, "<init>", "(Ljava/lang/String;I)V")
            println("GOT init")
            val optionsObject = env.newObject(
                optionsClass,
                init,
                {
                    l = env.newUtfString(linkingHello)
                }, {
                    i = linkingNumber
                }
            )
            println("CREATE Main.Linking")

            env.callStaticVoidMethod(jcls, jclEntry, {
                l = optionsObject
            })
            println("CALLED cobolentry")

            val changedI = env.callIntMethod(optionsObject, env.getMethod(optionsClass, "getI", "()I"))
            val changedS =
                env.callObjectMethodA(optionsObject, env.getMethod(optionsClass, "getS", "()Ljava/lang/String;"))

            println("$changedI, ${env.pointed.pointed!!.GetStringChars!!(env, changedS, null)!!.toKString()}")
        }
    } finally {
        println("FINALLY")
        if (jvm.value != null) {
            println("SHUTDOWN JVM")
            jvm.ptr[0]!!.pointed.pointed!!.DestroyJavaVM!!(jvm.ptr[0])
            println("JVM DESTROYED")
        }
        println("FINISHED")
    }
    jvmArena.clear()
}

private fun CPointer<JNIEnvVar>.newUtfString(string: String): jstring = memScoped {
    pointed.pointed!!.NewStringUTF!!(this@newUtfString, string.cstr.ptr)!!
}

private fun CPointer<JNIEnvVar>.callIntMethod(
    jobject: jobject,
    method: jmethodID,
    vararg values: jvalue.() -> Unit
): jint = memScoped {
    val args = allocArray<jvalue>(values.size) {
        values[it].invoke(this)
    }
    pointed.pointed!!.CallIntMethodA!!(this@callIntMethod, jobject, method, args)
}

private fun CPointer<JNIEnvVar>.callObjectMethodA(
    jobject: jobject,
    method: jmethodID,
    vararg values: jvalue.() -> Unit
): jobject = memScoped {
    val args = allocArray<jvalue>(values.size) {
        values[it].invoke(this)
    }
    pointed.pointed!!.CallObjectMethodA!!(this@callObjectMethodA, jobject, method, args)!!
}

private fun CPointer<JNIEnvVar>.callStaticVoidMethod(
    jClass: jclass,
    method: jmethodID,
    vararg values: jvalue.() -> Unit
): Unit = memScoped {
    val args = allocArray<jvalue>(values.size) {
        values[it].invoke(this)
    }
    pointed.pointed!!.CallStaticVoidMethodA!!(this@callStaticVoidMethod, jClass, method, args)
}

private fun CPointer<JNIEnvVar>.getField(jobject: jobject, name: String, type: String): jfieldID {
    return memScoped {
        pointed.pointed!!.GetFieldID!!(this@getField, jobject, name.cstr.ptr, type.cstr.ptr)!!
    }
}

private fun CPointer<JNIEnvVar>.getMethod(jClass: jclass, name: String, parameter: String): jmethodID {
    return memScoped {
        pointed.pointed!!.GetMethodID!!(this@getMethod, jClass, name.cstr.ptr, parameter.cstr.ptr)!!
    }
}

private fun CPointer<JNIEnvVar>.getStaticMethod(jClass: jclass, name: String, parameter: String): jmethodID =
    memScoped {
        pointed.pointed!!.GetStaticMethodID!!(this@getStaticMethod, jClass, name.cstr.ptr, parameter.cstr.ptr)!!
    }

private fun CPointer<JNIEnvVar>.newObject(
    jClass: jclass,
    method: jmethodID,
    vararg values: jvalue.() -> Unit
): jobject = memScoped {
    val args = allocArray<jvalue>(values.size) {
        values[it].invoke(this)
    }
    pointed.pointed!!.NewObjectA!!(this@newObject, jClass, method, args)!!
}

private fun CPointer<JNIEnvVar>.findClass(className: String): jclass = memScoped {
    val p = requireNotNull(pointed.pointed) { "ENV ERROR in findClass: pointed.pointed was null" }
    val FindClass = requireNotNull(p.FindClass) { "p.FindClass was null" }
    val jlass = FindClass(this@findClass, className.cstr.ptr)
    requireNotNull(jlass) { "jclass for $className was not found" }
}
