const print = @import("std").debug.print;
const c_allocator = @import("std").heap.c_allocator;
const dlopen = @import("std").c.dlopen;
const dlclose = @import("std").c.dlclose;
const dlsym = @import("std").c.dlsym;
const dlfcn = @cImport(@cInclude("dlfcn.h"));
const jni = @cImport(@cInclude("jni.h"));

const JaunchErrors = enum(c_int) {
    ERROR_DLOPEN,
    ERROR_DLSYM,
    ERROR_CREATE_JAVA_VM,
    ERROR_FIND_CLASS,
    ERROR_GET_STATIC_METHOD_ID
};

pub export fn launch_jvm(
    libjvm_path_arg: [*c]const u8,
    jvm_argc_arg: c_int,
    jvm_argv_arg: [*c][*c]const u8,
    main_class_name_arg: [*c]const u8,
    main_argc_arg: c_int,
    main_argv_arg: [*c][*c]const u8
) c_int {
    // cast to a null terminated sentinel pointer
    const libjvm_path = @as([*:0]const u8, libjvm_path_arg);
    print("{s}\n", .{libjvm_path});

    // Load libjvm.
    print("LOADING LIBJVM\n", .{});
    const jvm_library = dlopen(libjvm_path, dlfcn.RTLD_NOW | dlfcn.RTLD_GLOBAL);
    if (jvm_library == null) {
        print("Error loading libjvm: {s}\n", .{dlfcn.dlerror()});
        return @intFromEnum(JaunchErrors.ERROR_DLOPEN);
    }

    // Load JNI_CreateJavaVM function.
    print("LOADING JNI_CreateJavaVM\n", .{});
    var JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
    if (JNI_CreateJavaVM == null) {
        print("Error finding JNI_CreateJavaVM: {s}\n", .{dlfcn.dlerror()});
        return @intFromEnum(JaunchErrors.ERROR_DLSYM);
    }
    //_ = JNI_CreateJavaVM;
    var create_java : *const @TypeOf(jni.JNI_CreateJavaVM) = @ptrCast(JNI_CreateJavaVM.?);

    print("{s}\n", .{create_java});

    if (jvm_argv_arg != null) {
        //cast to a slice of null terminated sentinel pointers
        const jvm_argc : usize = @intCast(jvm_argc_arg);
        const jvm_argv : [][*:0]const u8 = @ptrCast(jvm_argv_arg[0..jvm_argc]);

        for (jvm_argv) |x| {
            print("{s}\n", .{x});
        }

        // Populate VM options.
        print("POPULATING VM OPTIONS\n", .{});
        const sentinel_option = jni.JavaVMOption{.optionString = null, .extraInfo = null};
        if(c_allocator.allocSentinel(jni.JavaVMOption, jvm_argc, sentinel_option) catch null) |vmOptions| {
            print("{s}\n", .{@typeName(@TypeOf(vmOptions))});
            for (vmOptions, jvm_argv) |*vmOption, jvm_arg| {
                vmOption.* = jni.JavaVMOption{
                    .optionString = @constCast(jvm_arg),
                    .extraInfo = null
                };
            }

            // Populate VM init args.
            print("POPULATING VM INIT ARGS\n", .{});
            const vmInitArgs = jni.JavaVMInitArgs {
                .version = jni.JNI_VERSION_1_8,
                .options = @ptrCast(@constCast(&vmOptions)),
                .nOptions = @intCast(jvm_argc),
                .ignoreUnrecognized = jni.JNI_FALSE
            };

            // Create the JVM.
            print("CREATING JVM\n", .{});
            var jvm : ?*jni.JavaVM = null;
            var env : ?*jni.JNIEnv = null;
            if (create_java(&jvm, @ptrCast(&env), @constCast(&vmInitArgs)) != jni.JNI_OK) {
                print("Error creating Java VM\n", .{});
                _ = dlclose(jvm_library.?);
                return @intFromEnum(JaunchErrors.ERROR_CREATE_JAVA_VM);
            }


        } else {
            return @intFromEnum(JaunchErrors.ERROR_CREATE_JAVA_VM);
        }
    }

    // cast to a null terminated sentinel pointer
    const main_class_name = @as([*:0]const u8, main_class_name_arg);
    print("{s}\n", .{main_class_name});

    if (main_argv_arg != null) {
        //cast to a slice of null terminated sentinel pointers
        const main_argv : [][*:0]const u8 = @ptrCast(main_argv_arg[0..@intCast(main_argc_arg)]);

        for (main_argv) |x| {
            print("{s}\n", .{x});
        }
    }

    return 0;
}


pub fn main() void {
    const libjvmPath = "/usr/lib/jvm/default-java/lib/server/libjvm.so";

    const jvmArgc = 0; // Set the appropriate values
    var jvmArgv: [*c][*c]const u8 = undefined;
    jvmArgv[0] = "-Dfoo=bar";
    jvmArgv[1] = "-Dyes=weCan";

    const mainClassName = "MainClass"; // Replace with the actual main class name

    const mainArgc = 0; // Set the appropriate values
    const mainArgv: [*c][*c]const u8 = undefined;

    const result = launch_jvm(libjvmPath, jvmArgc, jvmArgv, mainClassName, mainArgc, mainArgv);

    print("launch_jvm result: {}\n", .{result});
}
