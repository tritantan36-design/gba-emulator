add_library(gba_audio STATIC audio_output.cpp)
target_include_directories(gba_audio PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})
target_link_libraries(gba_audio PUBLIC oboe)
target_compile_definitions(gba_audio PUBLIC $<$<CONFIG:Debug>:GBA_TEST_HOOKS>)
