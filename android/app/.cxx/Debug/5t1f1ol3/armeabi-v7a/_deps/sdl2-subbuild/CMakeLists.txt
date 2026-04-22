# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.22.1-g37088a8)

# We name the project and the target for the ExternalProject_Add() call
# to something that will highlight to the user what we are working on if
# something goes wrong and an error message is produced.

project(sdl2-populate NONE)


# Pass through things we've already detected in the main project to avoid
# paying the cost of redetecting them again in ExternalProject_Add()
set(GIT_EXECUTABLE [==[/usr/bin/git]==])
set(GIT_VERSION_STRING [==[2.50.1 (Apple Git-155)]==])
set_property(GLOBAL PROPERTY _CMAKE_FindGit_GIT_EXECUTABLE_VERSION
  [==[/usr/bin/git;2.50.1 (Apple Git-155)]==]
)


include(ExternalProject)
ExternalProject_Add(sdl2-populate
                     "UPDATE_DISCONNECTED" "False" "GIT_REPOSITORY" "https://github.com/libsdl-org/SDL.git" "GIT_TAG" "release-2.32.8" "GIT_SHALLOW" "TRUE" "OVERRIDE_FIND_PACKAGE"
                    SOURCE_DIR          "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/armeabi-v7a/_deps/sdl2-src"
                    BINARY_DIR          "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/armeabi-v7a/_deps/sdl2-build"
                    CONFIGURE_COMMAND   ""
                    BUILD_COMMAND       ""
                    INSTALL_COMMAND     ""
                    TEST_COMMAND        ""
                    USES_TERMINAL_DOWNLOAD  YES
                    USES_TERMINAL_UPDATE    YES
)


