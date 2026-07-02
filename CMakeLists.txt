cmake_minimum_required(VERSION 3.16)
project(scorecard C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

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

FetchContent_Declare(
    mongoose
    GIT_REPOSITORY https://github.com/cesanta/mongoose.git
    GIT_TAG 7.15
)
FetchContent_MakeAvailable(mongoose)

add_executable(scorecard
    src/main.c
    src/storage.c
    src/routes.c
    src/game_view.c
    ${mongoose_SOURCE_DIR}/mongoose.c
)
target_include_directories(scorecard PRIVATE ${cjson_SOURCE_DIR} ${mongoose_SOURCE_DIR})
target_link_libraries(scorecard PRIVATE cjson)
find_package(Threads REQUIRED)
target_link_libraries(scorecard PRIVATE Threads::Threads)

enable_testing()

add_executable(test_storage
    tests/test_storage.c
    src/storage.c
)
target_include_directories(test_storage PRIVATE src ${cjson_SOURCE_DIR})
target_link_libraries(test_storage PRIVATE cjson)

# storage.c operates on a "data" dir relative to the process's cwd, so tests
# run from a scratch directory under the build tree to avoid touching any
# real data/ a developer has sitting at the project root.
set(TEST_STORAGE_CWD ${CMAKE_CURRENT_BINARY_DIR}/test_run)
file(MAKE_DIRECTORY ${TEST_STORAGE_CWD})
add_test(NAME storage_tests COMMAND test_storage WORKING_DIRECTORY ${TEST_STORAGE_CWD})

# compute_game_view is a pure function (no file I/O), so this test needs no
# cwd isolation.
add_executable(test_game_view
    tests/test_game_view.c
    src/game_view.c
)
target_include_directories(test_game_view PRIVATE src ${cjson_SOURCE_DIR})
target_link_libraries(test_game_view PRIVATE cjson)
add_test(NAME game_view_tests COMMAND test_game_view)
