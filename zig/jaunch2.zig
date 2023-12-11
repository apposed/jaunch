const std = @import("std");

const c = @cImport(@cInclude("/home/curtis/code/launcher/java-launcher/jaunch/jaunch.h"));

pub fn main() void {
    const libjvmPath = "/usr/lib/jvm/default-java/lib/server/libjvm.so";

    const jvmArgc = 0; // Set the appropriate values
    var jvmArgv: [*c][*c]const u8 = undefined;
    jvmArgv[0] = "-Dfoo=bar";
    jvmArgv[1] = "-Dyes=weCan";

    const mainClassName = "MainClass"; // Replace with the actual main class name

    const mainArgc = 0; // Set the appropriate values
    const mainArgv: [*c][*c]const u8 = undefined;

    const result = c.launch_java(libjvmPath, jvmArgc, jvmArgv, mainClassName, mainArgc, mainArgv);

    std.debug.print("launch_java result: {}\n", .{result});
}
