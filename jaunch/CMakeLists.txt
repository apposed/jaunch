cmake_minimum_required(VERSION 3.2.0)

# Default policy is from 3.4.0
cmake_policy(VERSION 3.4.0)

# Set project metadata
project(jaunch LANGUAGES C VERSION 1 DESCRIPTION "Launch Java your way")

# Add JNI include directory
find_package(JNI REQUIRED)
if (JNI_FOUND)
	message (STATUS "JNI_INCLUDE_DIRS=${JNI_INCLUDE_DIRS}")
	message (STATUS "JNI_LIBRARIES=${JNI_LIBRARIES}")
endif()

if (APPLE)
	SET(JAVA_INCLUDE_PATH "$ENV{JAVA_HOME}/include")
	SET(JAVA_INCLUDE_PATH2 "$ENV{JAVA_HOME}/include/darwin")
endif()
include_directories(${JAVA_INCLUDE_PATH})
include_directories(${JAVA_INCLUDE_PATH2})
include_directories(${JNI_INCLUDE_DIRS})

# Find Frameworks on macOS
#if (APPLE)
#	find_library(CORE_FOUNDATION CoreFoundation)
#	if (NOT CORE_FOUNDATION)
#		message(FATAL_ERROR "AudioUnit not found")
#	endif()
#
#	find_library(APPLICATION_SERVICES ApplicationServices)
#	if (NOT APPLICATION_SERVICES)
#		message(FATAL_ERROR "ApplicationServices not found")
#	endif()
#endif (APPLE)

add_library(jaunch SHARED jaunch.c)
set_target_properties(jaunch PROPERTIES
	VERSION ${PROJECT_VERSION}
	SOVERSION ${PROJECT_VERSION_MAJOR}
	PUBLIC_HEADER jaunch.h)

# Define libdl name
IF (NOT LIB_DL)
	IF(${CMAKE_SYSTEM_NAME} MATCHES "Windows")
		# Windows
		SET(LIB_DL "kernel32")
	ELSEIF(${CMAKE_SYSTEM_NAME} MATCHES "Linux")
		# Linux
		SET(LIB_DL "dl")
	ENDIF()
ENDIF()

# Define libpthread name
IF (NOT LIB_PTHREAD)
	IF(${CMAKE_SYSTEM_NAME} MATCHES "Linux")
		# Linux
		SET(LIB_PTHREAD "pthread")
	ENDIF()
ENDIF()

# Check target architecture
IF (${TARGET_ARCHITECTURE} MATCHES "x86")
	SET(ARCHITECTURE "-m32")
ENDIF ()

# Add projects include directory
include_directories(".")

# Set compiler flags and compile
if (APPLE)
	set(CMAKE_OSX_ARCHITECTURES "arm64; x86_64")
	set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -mmacosx-version-min=10.6")
elseif (${CMAKE_SYSTEM_NAME} MATCHES "Windows")
	# Windows (64-bit)
	SET(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -D__int64=int64_t")
elseif (${CMAKE_SYSTEM_NAME} MATCHES "Linux")
	# Linux
	SET(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fno-stack-protector")
endif (APPLE)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} ${ARCHITECTURE} ${DEBUG_OPTION}")
#add_executable (${EXECUTABLE_NAME} WIN32 ${SOURCE_FILES})

# Set linker flags and compile
#set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${ARCHITECTURE}")
target_link_libraries(jaunch ${LIB_DL} ${LIB_PTHREAD}) # ${CORE_FOUNDATION} ${APPLICATION_SERVICES})
