cmake_minimum_required(VERSION 3.16)
project(scorecard C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

find_package(PkgConfig REQUIRED)
pkg_check_modules(MICROHTTPD REQUIRED libmicrohttpd)

include(FetchContent)
set(ENABLE_CJSON_TEST OFF CACHE BOOL "" FORCE)
set(ENABLE_CJSON_UTILS OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    cjson
    GIT_REPOSITORY https://github.com/DaveGamble/cJSON.git
    GIT_TAG v1.7.18
)
FetchContent_MakeAvailable(cjson)

add_executable(scorecard src/main.c)
target_include_directories(scorecard PRIVATE ${MICROHTTPD_INCLUDE_DIRS} ${cjson_SOURCE_DIR})
target_link_libraries(scorecard PRIVATE ${MICROHTTPD_LIBRARIES} cjson)
target_compile_options(scorecard PRIVATE ${MICROHTTPD_CFLAGS_OTHER})
