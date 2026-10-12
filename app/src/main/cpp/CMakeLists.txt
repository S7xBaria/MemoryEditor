cmake_minimum_required(VERSION 3.22.1)
project("memedit")

add_library(memedit SHARED memedit.cpp)
find_library(log-lib log)
target_link_libraries(memedit ${log-lib})
