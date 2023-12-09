#ifndef JAUNCH_H
#define JAUNCH_H

extern int launch_java(const char *libjvm_path, const int jvm_argc, const char *jvm_argv[],
	const char *main_class_name, const int main_argc, const char *main_argv[]);

#endif
