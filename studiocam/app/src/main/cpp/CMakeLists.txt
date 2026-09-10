cmake_minimum_required(VERSION 3.22.1)
project(studiocamaudio)

find_package(oboe REQUIRED CONFIG)

add_library(studiocam_audio SHARED
    native-lib.cpp
    AudioEngine.cpp
    DelayEffect.cpp
    ReverbEffect.cpp
)

target_link_libraries(studiocam_audio
    oboe::oboe
    android
    log
)
