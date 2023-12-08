#include <stdio.h>
#include <dlfcn.h>

#include "jni.h"

#define ERROR_DLOPEN 1
#define ERROR_DLSYM 2
#define ERROR_CREATE_JAVA_VM 3
#define ERROR_FIND_CLASS 4
#define ERROR_GET_STATIC_METHOD_ID 5

int launch_java(const char *libjvm_path, const int jvm_argc, const char *jvm_argv[],
	const char *main_class_name, const int main_argc, const char *main_argv[])
{
	int i;

	// Load libjvm.
	printf("LOADING LIBJVM\n");
	void *jvm_library = dlopen(libjvm_path, RTLD_NOW | RTLD_GLOBAL);
	if (!jvm_library) {
		fprintf(stderr, "Error loading libjvm: %s\n", dlerror());
		return ERROR_DLOPEN;
	}

	// Load JNI_CreateJavaVM function.
	printf("LOADING JNI_CreateJavaVM\n");
	static jint (*JNI_CreateJavaVM)(JavaVM **pvm, void **penv, void *args);
	JNI_CreateJavaVM = dlsym(jvm_library, "JNI_CreateJavaVM");
	if (!JNI_CreateJavaVM) {
		fprintf(stderr, "Error finding JNI_CreateJavaVM: %s\n", dlerror());
		dlclose(jvm_library);
		return ERROR_DLSYM;
	}

	// Populate VM options.
	printf("POPULATING VM OPTIONS\n");
	JavaVMOption vmOptions[jvm_argc + 1];
	for (i = 0; i < jvm_argc; i++) {
		vmOptions[i].optionString = jvm_argv[i];
	}
	vmOptions[jvm_argc].optionString = NULL;

	// Populate VM init args.
	printf("POPULATING VM INIT ARGS\n");
	JavaVMInitArgs vmInitArgs;
	vmInitArgs.version = JNI_VERSION_1_8;
	vmInitArgs.options = vmOptions;
	vmInitArgs.nOptions = jvm_argc;
	vmInitArgs.ignoreUnrecognized = JNI_FALSE;

	// Create the JVM.
	printf("CREATING JVM\n");
	JavaVM *jvm;
	JNIEnv *env;
	if (JNI_CreateJavaVM(&jvm, (void **)&env, &vmInitArgs) != JNI_OK) {
		fprintf(stderr, "Error creating Java VM\n");
		dlclose(jvm_library);
		return ERROR_CREATE_JAVA_VM;
	}

	// Find the main class.
	printf("FINDING MAIN CLASS\n");
	jclass mainClass = (*env)->FindClass(env, main_class_name);
	if (mainClass == NULL) {
		fprintf(stderr, "Error finding class %s\n", main_class_name);
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_FIND_CLASS;
	}

	// Find the main method.
	printf("FINDING MAIN METHOD\n");
	jmethodID mainMethod = (*env)->GetStaticMethodID(env, mainClass, "main", "([Ljava/lang/String;)V");
	if (mainMethod == NULL) {
		fprintf(stderr, "Error finding main method\n");
		(*jvm)->DestroyJavaVM(jvm);
		dlclose(jvm_library);
		return ERROR_GET_STATIC_METHOD_ID;
	}

	// Populate main method arguments.
	printf("FINDING MAIN METHOD ARGUMENTS\n");
	jobjectArray javaArgs = (*env)->NewObjectArray(env, main_argc, (*env)->FindClass(env, "java/lang/String"), NULL);
	for (i = 0; i < main_argc; i++) {
		(*env)->SetObjectArrayElement(env, javaArgs, i, (*env)->NewStringUTF(env, main_argv[i]));
	}

	// Invoke the main method.
	printf("INVOKING MAIN METHOD\n");
	(*env)->CallStaticVoidMethodA(env, mainClass, mainMethod, (jvalue *)&javaArgs);

	printf("DETACHING CURRENT THREAD\n");
	if ((*jvm)->DetachCurrentThread(jvm)) {
		fprintf(stderr, "Could not detach current thread");
	}

	// Clean up.
	printf("DESTROYING JAVA VM\n");
	(*jvm)->DestroyJavaVM(jvm);
	printf("CLOSING LIBJVM\n");
	dlclose(jvm_library);
	printf("GOODBYE\n");
}

int main() {
	const char *libjvm = "/usr/lib/jvm/default-java/lib/server/libjvm.so";
	int jvm_argc = 5;
	const char *jvm_argv[] = {
		"-Xmx333m",
		"--add-opens=java.base/java.lang=ALL-UNNAMED",
		"--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED",
		"-Djfoo=jbar",
		"-Djava.class.path=" \
			"/home/curtis/Applications/Fiji.app/jars/ahocorasick-0.2.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/aircompressor-0.21.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/alphanumeric-comparator-1.4.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/animal-sniffer-annotations-1.23.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/annotations-13.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ant-1.10.13.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ant-launcher-1.10.13.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/antlr-3.5.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/antlr.antlr-2.7.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/antlr-runtime-3.5.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/api-common-2.9.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/args4j-2.33.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/asm-9.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/asm-analysis-9.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/asm-commons-9.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/asm-tree-9.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/asm-util-9.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/autocomplete-3.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/auto-value-annotations-1.10.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/aws-java-sdk-core-1.12.465.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/aws-java-sdk-kms-1.12.465.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/aws-java-sdk-s3-1.12.465.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/base-18.09.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/base64-2.3.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batch-processor-0.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-anim-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-awt-util-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-bridge-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-constants-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-css-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-dom-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-ext-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-gvt-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-i18n-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-parser-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-script-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-shared-resources-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-svg-dom-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-svggen-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-util-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/batik-xml-1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bcpkix-jdk15on-1.62.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bcprov-jdk15on-1.62.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bigdataviewer-core-10.4.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bigdataviewer-vistools-1.0.0-beta-32.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bij-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/formats-api-7.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/formats-bsd-7.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/formats-gpl-7.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/jai_imageio-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/jxrlib-all-0.2.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/metakit-5.3.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-codecs-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-common-6.0.20.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-jai-0.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-mdbtools-5.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-poi-5.3.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/ome-xml-6.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/specification-6.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bio-formats/turbojpeg-7.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/blas-0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bounce-0.18.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bsh-2.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/bytelist-1.0.15.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/caffeine-2.9.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/cdm-core-5.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/checker-qual-3.34.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/Clipper-6.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/clojure-1.8.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/codemodel-2.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/collections-generic-4.01.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-beanutils-1.9.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-codec-1.15.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-collections-3.2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-collections4-4.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-compress-1.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-io-2.11.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-lang-2.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-lang3-3.12.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-logging-1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-math-2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-math3-3.6.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-pool2-2.11.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-text-1.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/commons-vfs2-2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/compiler-interface-1.3.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/conscrypt-openjdk-uber-2.5.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/csbdeep-0.6.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/datasets-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/denoiseg-0.6.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/directories-26.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/dirgra-0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ejml-0.25.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/error_prone_annotations-2.19.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/eventbus-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/f2jutil-0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/failureaccess-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ffmpeg-5.1.2-1.5.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/fiji-2.14.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/fiji-compat-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/fiji-lib-2.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/fiji-links-0.0.1-SNAPSHOT.jar@:" \
			"/home/curtis/Applications/Fiji.app/jars/FilamentDetector-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/filters-2.0.235.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/flatlaf-3.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/fontchooser-2.5.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gapic-google-cloud-storage-v2-2.22.1-alpha.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gax-2.26.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gax-grpc-2.26.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gax-httpjson-0.111.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gcc-runtime-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gentyref-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gluegen-rt-2.4.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-api-client-2.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-api-services-cloudresourcemanager-v1-rev20230129-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-api-services-storage-v1-rev20230301-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-auth-library-credentials-1.16.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-auth-library-oauth2-http-1.16.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-cloud-core-2.16.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-cloud-core-grpc-2.16.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-cloud-core-http-2.16.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-cloud-resourcemanager-1.18.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-cloud-storage-2.22.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-apache-v2-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-appengine-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-gson-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-jackson2-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-http-client-xml-1.43.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/google-oauth-client-1.34.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gpars-1.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/graphics-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grDevices-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-dateutil-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-json-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-jsr223-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-swing-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-templates-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/groovy-xml-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-alts-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-api-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-auth-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-context-1.55.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-core-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-googleapis-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-google-cloud-storage-v2-2.22.1-alpha.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-grpclb-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-netty-shaded-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-protobuf-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-protobuf-lite-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-services-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-stub-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/grpc-xds-1.54.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/gson-2.10.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/guava-31.1-jre.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/httpclient-4.5.14.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/httpcore-4.4.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/httpmime-4.5.14.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/httpservices-5.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/icu4j-59.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ij-1.54f.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ij1-patcher-1.2.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/image4j-0.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-2.14.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-common-2.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-deprecated-0.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-launcher-6.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-legacy-1.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-mesh-0.8.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-mesh-io-0.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-modelzoo-0.9.10.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-notebook-0.7.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-ops-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-plugins-batch-0.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-plugins-commands-0.8.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-plugins-tools-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-plugins-uploader-ssh-0.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-plugins-uploader-webdav-0.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-scripting-0.8.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-tensorflow-1.1.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-ui-awt-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-ui-swing-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imagej-updater-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-6.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-algorithm-0.13.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-algorithm-fft-0.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-algorithm-gpl-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-cache-1.0.0-beta-17.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-ij-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-label-multisets-0.11.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-realtransform-4.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-roi-0.14.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/imglib2-ui-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/invokebinder-1.10.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ion-java-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/istack-commons-runtime-3.0.12.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/itextpdf-5.5.13.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ivy-2.5.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/j2objc-annotations-2.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/j3dcore-1.6.0-scijava-2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/j3dutils-1.6.0-scijava-2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackrabbit-webdav-2.21.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-annotations-2.14.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-core-2.14.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-databind-2.14.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-dataformat-cbor-2.14.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-dataformat-yaml-2.14.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jackson-jq-1.0.0-preview.20191208.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jai-codec-1.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jai-core-1.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jakarta.activation-api-1.2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jakarta.xml.bind-api-2.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jama-1.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/java-bioimage-io-0.3.9.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/javacpp-1.5.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/java-cup-11b-20160615.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/javaGeom-0.11.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/java-sizeof-0.0.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/javassist-3.29.2-GA.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/javax.annotation-api-1.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/javax.servlet-api-3.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jaxb-runtime-2.3.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jblosc-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jclipboardhelper-0.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jcodings-1.0.58.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jcommander-1.48.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jcommon-1.0.24.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jdatepicker-1.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jdom2-2.0.6.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jep-2.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jffi-1.3.11.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jffi-1.3.11-native.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jfilechooser-bookmarks-0.1.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jfreechart-1.5.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jfreesvg-3.4.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jgoodies-common-1.7.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jgoodies-forms-1.7.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jgrapht-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jgrapht-core-1.4.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jgraphx-4.2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jhdf5-19.04.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jheaps-0.14.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jhotdraw-7.6.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jitk-tps-3.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jline-2.14.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jline-native-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jline-reader-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jline-terminal-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jline-terminal-jna-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jmespath-java-1.12.465.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jna-5.13.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-a64asm-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-constants-0.10.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-enxio-0.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-ffi-2.2.13.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-netdb-1.1.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-posix-3.1.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-unixsocket-0.17.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jnr-x86asm-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/joda-time-2.12.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jogl-all-2.4.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/joml-1.10.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/joni-2.1.48.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jpedalSTD-2.80b11.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jply-0.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jruby-core-9.1.17.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jruby-stdlib-9.1.17.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jsch-0.1.55.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/json-20230227.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jsoup-1.7.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jsr166y-1.7.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jsr305-3.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jtransforms-2.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jung-api-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jung-graph-impl-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/JWlz-1.4.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jython-shaded-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jython-slim-2.7.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/jzlib-1.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/Kappa-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/kotlin-stdlib-1.8.22.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/kotlin-stdlib-common-1.8.22.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/kotlin-stdlib-jdk7-1.8.22.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/kotlin-stdlib-jdk8-1.8.22.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/kryo-5.5.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/labkit-pixel-classification-0.1.17.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/labkit-ui-0.3.11.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/languagesupport-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/lapack-0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/legacy-imglib1-1.1.10.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/libtensorflow-1.15.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/libtensorflow_jni-1.15.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/log4j-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/logback-classic-1.2.12.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/logback-core-1.2.12.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/lz4-java-1.8.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/mapdb-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/markdownj-0.3.0-1.0.2b4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/maven-scm-api-1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/maven-scm-provider-svn-commons-1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/maven-scm-provider-svnexe-1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/metadata-extractor-2.18.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/methods-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/miglayout-core-5.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/miglayout-swing-5.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/mines-jtk-20151125.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/minimaven-2.2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/minio-5.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/minlog-1.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/modulator-1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/mpicbg-1.5.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/mtj-1.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/multiverse-core-0.7.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n2v-0.8.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-2.5.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-aws-s3-3.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-blosc-1.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-google-cloud-3.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-hdf5-1.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-ij-3.2.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-imglib2-5.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/n5-zarr-0.0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/nailgun-server-0.9.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/native-lib-loader-2.4.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netcdf-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netlib-java-0.9.3-renjin-patched-2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-buffer-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-codec-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-common-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-handler-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-resolver-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/netty-transport-4.1.45.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/object-inspector-0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/objenesis-3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ojalgo-45.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/okhttp-4.11.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/okio-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/okio-jvm-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/OMEVisual-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/opencensus-api-0.31.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/opencensus-contrib-http-util-0.31.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/opencensus-proto-0.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/opencsv-5.7.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/op-finder-0.1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/options-1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/pal-optimization-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/parsington-3.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/perf4j-0.9.16.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/perfmark-api-0.26.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/picocli-4.7.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/plexus-utils-3.5.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/postgresql-42.6.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/prettytime-4.0.1.Final.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/proto-1.15.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/protobuf-java-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/protobuf-java-util-3.23.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/proto-google-cloud-resourcemanager-v3-1.18.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/proto-google-cloud-storage-v2-2.22.1-alpha.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/proto-google-common-protos-2.17.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/proto-google-iam-v1-1.12.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/re2j-1.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/reflectasm-1.11.9.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/regexp-1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/reload4j-1.2.25.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/renjin-appl-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/renjin-core-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/renjin-gnur-runtime-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/renjin-script-engine-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/rhino-1.7.14.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/rsyntaxtextarea-3.3.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scala3-compiler_3-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scala3-interfaces-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scala3-library_3-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scala-asm-9.4.0-scala-1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scala-library-2.13.10.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-0.45.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-bf-compat-4.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-cli-0.6.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-hdf5-0.2.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-jai-imageio-1.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-labeling-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-lifesci-0.9.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scifio-ome-xml-0.17.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-common-2.94.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-config-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-io-http-0.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-listeners-1.0.0-beta-3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-optional-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-plot-0.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-plugins-commands-0.2.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-plugins-platforms-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-plugins-text-markdown-0.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-plugins-text-plain-0.1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-search-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-table-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-ui-awt-0.1.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scijava-ui-swing-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/script-editor-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/script-editor-jython-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/script-editor-scala-0.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-beanshell-0.4.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-clojure-0.1.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-groovy-0.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-java-0.4.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-javascript-1.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-jruby-0.3.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-jython-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-renjin-0.2.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/scripting-scala-0.3.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/serializer-2.7.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/slf4j-api-1.7.36.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/snakeyaml-1.33.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/spim_data-2.2.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/sqlite-jdbc-3.28.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ST4-4.3.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/stats-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/swing-checkbox-tree-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/swing-worker-1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/swingx-1.6.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/T2-NIT-1.1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/T2-TreelineGraph-1.1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/tasty-core_3-3.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/tensorflow-1.15.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/threetenbp-1.6.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/TrackMate-7.11.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/trakem2-transform-1.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/trove4j-3.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/txw2-2.3.5.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/udunits-4.3.18.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/ui-behaviour-2.0.7.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/unsafe-fences-1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/util-interface-1.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/utils-0.8.1906.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/vecmath-1.6.0-scijava-2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/VectorGraphics2D-0.13.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/VectorString-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/VIB-lib-2.2.0.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/weave_jy2java-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/weka-dev-3.9.6.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xalan-2.7.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xchart-3.5.4.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xerbla-0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xercesImpl-2.12.2.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xml-apis-1.4.01.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xml-apis-ext-1.3.04.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xmlgraphics-commons-2.8.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xmpcore-6.1.11.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xpp3-1.1.4c.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/xz-1.9.jar:" \
			"/home/curtis/Applications/Fiji.app/jars/yecht-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/3D_Blob_Segmentation-3.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/3D_Objects_Counter-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/3D_Viewer-4.0.5.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/AnalyzeSkeleton_-3.4.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Anisotropic_Diffusion_2D-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Archipelago_Plugins-0.5.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Arrow_-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Auto_Local_Threshold-1.11.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Auto_Threshold-1.18.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/BalloonSegmentation_-3.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/bigdataviewer_fiji-6.2.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/bigwarp_fiji-7.0.7.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/bio-formats_plugins-7.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/blockmatching_-2.1.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Bug_Submitter-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/bUnwarpJ_-2.6.13.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Calculator_Plus-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Cell_Counter-3.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Colocalisation_Analysis-3.0.6.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Color_Histogram-2.0.7.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Color_Inspector_3D-2.5.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Colour_Deconvolution-3.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Correct_3D_Drift-1.0.6.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/CorrectBleach_-2.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/CPU_Meter-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/DeconvolutionLab_2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Descriptor_based_registration-2.1.8.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Dichromacy_-2.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Directionality_-2.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Feature_Detection-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Fiji_Archipelago-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Fiji_Developer-2.0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Fiji_Package_Maker-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Fiji_Plugins-3.1.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/FlowJ_-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/FS_Align_TrakEM2-2.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Graph_Cut-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Gray_Morphology-2.3.5.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/H5J_Loader_Plugin-1.1.5.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/HDF5_Vibez-1.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Helmholtz_Analysis-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/ij_ridge_detect-1.4.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/IJ_Robot-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Image_5D-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Image_Expression_Parser-3.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Interactive_3D_Surface_Plot-3.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/IO_-4.2.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/IsoData_Classifier-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Kuwahara_Filter-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/KymographBuilder-3.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Lasso_and_Blow_Tool-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/level_sets-1.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Linear_Kuwahara-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/LocalThickness_-4.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/LSM_Reader-4.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/LSM_Toolbox-4.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Manual_Tracking-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/M_I_P-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/mpicbg_-1.5.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/MTrack2_-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Multi_Kymograph-3.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/n5-viewer_fiji-5.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/panorama_-3.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/PIV_analyser-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/QuickPALM_-1.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/RATS_-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Reconstruct_Reader-2.0.5.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/register_virtual_stack_slices-3.0.8.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/registration_3d-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Samples_-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Series_Labeler-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Sholl_Analysis-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Simple_Neurite_Tracer-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Siox_Segmentation-1.0.5.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Skeletonize3D_-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/SPIM_Opener-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/SPIM_Registration-5.0.25.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/SplineDeformationGenerator_-2.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Stack_Manipulation-2.1.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/StarDist_-0.3.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Statistical_Region_Merging-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Stitching_-3.1.9.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Sync_Win-1.7-fiji4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Thread_Killer-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Time_Lapse-2.1.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Time_Stamper-2.1.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/ToAST_-25.0.2.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/TopoJ_-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/TrackMate_-0.0.0-STUB.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Trainable_Segmentation-3.3.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/TrakEM2_-1.3.8.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/TrakEM2_Archipelago-2.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/trakem2_tps-2.0.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Vaa3d_Reader-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Vaa3d_Writer-1.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/VIB_-3.0.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Video_Editing-2.0.1.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/View5D_-2.5.0.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Volume_Calculator-2.0.3.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/Volume_Viewer-2.01.4.jar:" \
			"/home/curtis/Applications/Fiji.app/plugins/z_spacing-1.1.2.jar"
	};

	const char *main_class_name = "sc/fiji/Main";
	int main_argc = 1;
	const char *main_argv[] = {"-Dmfoo=mbar"};

	int result = launch_java(libjvm, jvm_argc, jvm_argv, main_class_name, main_argc, main_argv);

	return result;
}
