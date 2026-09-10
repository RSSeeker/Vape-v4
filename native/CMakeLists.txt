cmake_minimum_required(VERSION 3.21)
project(Vape421Native VERSION 1.0 LANGUAGES C CXX RC)

# 附件 exe 命名版本号（由 Gradle 以 -DVAPE421_VERSION=... 传入），用于输出
# Vape-v<VERSION>.exe。未传入时回退默认，避免生成空版本名。
if(NOT DEFINED VAPE421_VERSION OR VAPE421_VERSION STREQUAL "")
    set(VAPE421_VERSION "4.21")
endif()

if(NOT WIN32)
    message(FATAL_ERROR "Vape421Native is a Windows-only test bridge")
endif()
if(NOT MSVC)
    message(FATAL_ERROR "The audited test bridge requires the MSVC x64 toolchain")
endif()
if(NOT CMAKE_SIZEOF_VOID_P EQUAL 8)
    message(FATAL_ERROR "Only x64 Minecraft/JVM targets are supported")
endif()

set(VAPE421_JAVA_HOME "$ENV{JAVA_HOME}" CACHE PATH "JDK containing JNI/JVMTI headers")
if(NOT VAPE421_JAVA_HOME)
    foreach(candidate
            "C:/Program Files/Java/jdk1.8.0_301"
            "C:/Program Files/Java/jdk-17")
        if(EXISTS "${candidate}/include/jni.h")
            set(VAPE421_JAVA_HOME "${candidate}")
            break()
        endif()
    endforeach()
endif()

find_path(VAPE421_JNI_INCLUDE jni.h
        HINTS "${VAPE421_JAVA_HOME}/include" REQUIRED)
find_path(VAPE421_JNI_MD_INCLUDE jni_md.h
        HINTS "${VAPE421_JAVA_HOME}/include/win32" REQUIRED)
find_path(VAPE421_JVMTI_INCLUDE jvmti.h
        HINTS "${VAPE421_JAVA_HOME}/include" REQUIRED)

set(VAPE421_PRODUCT_JAR "" CACHE FILEPATH
        "Recovered product JAR embedded into Vape421Native.dll")
if(NOT VAPE421_PRODUCT_JAR OR NOT EXISTS "${VAPE421_PRODUCT_JAR}")
    message(FATAL_ERROR
            "VAPE421_PRODUCT_JAR must name an existing recovered product JAR")
endif()
get_filename_component(VAPE421_PRODUCT_JAR
        "${VAPE421_PRODUCT_JAR}" ABSOLUTE)
file(TO_CMAKE_PATH "${VAPE421_PRODUCT_JAR}" VAPE421_PRODUCT_JAR_RC_PATH)
configure_file(payload.rc.in generated/payload.rc @ONLY)
set_property(SOURCE "${CMAKE_CURRENT_BINARY_DIR}/generated/payload.rc"
        APPEND PROPERTY OBJECT_DEPENDS "${VAPE421_PRODUCT_JAR}")

add_library(Vape421Native SHARED
        dllmain.c
        loader_bootstrap.c
        native_bridge.c
        "${CMAKE_CURRENT_BINARY_DIR}/generated/payload.rc")
target_include_directories(Vape421Native PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${VAPE421_JNI_INCLUDE}"
        "${VAPE421_JNI_MD_INCLUDE}"
        "${VAPE421_JVMTI_INCLUDE}")
target_compile_definitions(Vape421Native PRIVATE
        WIN32_LEAN_AND_MEAN
        _CRT_SECURE_NO_WARNINGS)
target_compile_options(Vape421Native PRIVATE /W4 /utf-8)
target_link_libraries(Vape421Native PRIVATE user32 ws2_32)

# GUI loader (upstream VapeLoader): process selection -> inject. The only
# shipped artifact is this single-file exe with the product DLL embedded.
# NOTE: sources are collected via file(GLOB) instead of being listed inside
# add_executable(); a CMake 4.3 VS-generator quirk misclassifies .cpp sources
# that share a target with configure_file()-generated .rc sources as <None>.
set(VAPE421_LOADER_GUI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/loader_gui")
file(GLOB VAPE421_LOADER_GUI_SOURCES
        CONFIGURE_DEPENDS
        "${VAPE421_LOADER_GUI_DIR}/*.cpp"
        "${VAPE421_LOADER_GUI_DIR}/*.h"
)
add_executable(Vape421LoaderGui WIN32
        ${VAPE421_LOADER_GUI_SOURCES}
        "${CMAKE_CURRENT_SOURCE_DIR}/injector.c"
        "${VAPE421_LOADER_GUI_DIR}/assets.rc"
        "${VAPE421_LOADER_GUI_DIR}/icon.rc"
        "${VAPE421_LOADER_GUI_DIR}/app.manifest"
)
target_compile_features(Vape421LoaderGui PRIVATE cxx_std_17)
target_compile_definitions(Vape421LoaderGui PRIVATE
        WIN32_LEAN_AND_MEAN
        UNICODE _UNICODE NOMINMAX
        _CRT_SECURE_NO_WARNINGS)
target_compile_options(Vape421LoaderGui PRIVATE /W4 /permissive- /utf-8)
target_link_libraries(Vape421LoaderGui PRIVATE
        gdiplus
        winhttp
        shell32
        shlwapi
        ws2_32
)
target_include_directories(Vape421LoaderGui PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/loader_gui")

# Embed Vape421Native.dll into the GUI loader for single-file operation.
# The loader extracts resource 422 (RCDATA) to a temp file when no DLL sits
# beside the exe (see ControllerModel::materializeEmbeddedDll).
set(VAPE421_LOADER_GUI_DLL_RC
        "${CMAKE_CURRENT_BINARY_DIR}/generated/loader_gui_dll.rc")
add_custom_command(
        OUTPUT "${VAPE421_LOADER_GUI_DLL_RC}"
        COMMAND "${CMAKE_COMMAND}"
                "-DDLL_PATH=$<TARGET_FILE:Vape421Native>"
                "-DRC_OUT=${VAPE421_LOADER_GUI_DLL_RC}"
                -P "${CMAKE_CURRENT_SOURCE_DIR}/embed_dll.cmake"
        DEPENDS Vape421Native
        COMMENT "Embedding Vape421Native.dll into the GUI loader")
target_sources(Vape421LoaderGui PRIVATE "${VAPE421_LOADER_GUI_DLL_RC}")

include(CTest)
if(BUILD_TESTING)
    add_executable(Vape421BootstrapTest
            tests/loader_bootstrap_test.c
            loader_bootstrap.c)
    target_include_directories(Vape421BootstrapTest PRIVATE
            "${CMAKE_CURRENT_SOURCE_DIR}")
    target_compile_definitions(Vape421BootstrapTest PRIVATE
            WIN32_LEAN_AND_MEAN
            _CRT_SECURE_NO_WARNINGS)
    target_compile_options(Vape421BootstrapTest PRIVATE /W4 /utf-8)
    target_link_libraries(Vape421BootstrapTest PRIVATE ws2_32)
    add_test(NAME Vape421BootstrapStandalone
            COMMAND Vape421BootstrapTest standalone)
    add_test(NAME Vape421BootstrapHandoff COMMAND Vape421BootstrapTest)
    add_test(NAME Vape421BootstrapRejectsInvalid
            COMMAND Vape421BootstrapTest invalid)

    # Loader GUI language selection: reads the product's saved language from
    # .vapeclient\config.json and must fall back to Chinese on every failure.
    add_executable(Vape421LoaderStringsTest
            tests/loader_strings_test.cpp
            loader_gui/loader_strings.cpp)
    target_include_directories(Vape421LoaderStringsTest PRIVATE
            "${CMAKE_CURRENT_SOURCE_DIR}/loader_gui")
    target_compile_definitions(Vape421LoaderStringsTest PRIVATE
            WIN32_LEAN_AND_MEAN
            _CRT_SECURE_NO_WARNINGS)
    target_compile_options(Vape421LoaderStringsTest PRIVATE /W4 /utf-8)
    add_test(NAME Vape421LoaderStrings COMMAND Vape421LoaderStringsTest)
endif()

set_property(TARGET Vape421Native Vape421LoaderGui
        PROPERTY
        MSVC_RUNTIME_LIBRARY "MultiThreaded$<$<CONFIG:Debug>:Debug>DLL")

set_target_properties(Vape421Native PROPERTIES
        OUTPUT_NAME "Vape-v4.21Native")
set_target_properties(Vape421LoaderGui PROPERTIES
        OUTPUT_NAME "Vape-v${VAPE421_VERSION}")

set_target_properties(Vape421Native Vape421LoaderGui PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/dist")
foreach(configuration Debug Release RelWithDebInfo MinSizeRel)
    string(TOUPPER "${configuration}" configuration_upper)
    set_target_properties(Vape421Native Vape421LoaderGui PROPERTIES
            RUNTIME_OUTPUT_DIRECTORY_${configuration_upper}
            "${CMAKE_BINARY_DIR}/dist")
endforeach()
