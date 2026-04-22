
if(NOT "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitinfo.txt" IS_NEWER_THAN "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitclone-lastrun.txt")
  message(STATUS "Avoiding repeated git clone, stamp file is up to date: '/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitclone-lastrun.txt'")
  return()
endif()

execute_process(
  COMMAND ${CMAKE_COMMAND} -E rm -rf "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-src"
  RESULT_VARIABLE error_code
  )
if(error_code)
  message(FATAL_ERROR "Failed to remove directory: '/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-src'")
endif()

# try the clone 3 times in case there is an odd git clone issue
set(error_code 1)
set(number_of_tries 0)
while(error_code AND number_of_tries LESS 3)
  execute_process(
    COMMAND "/usr/bin/git"  clone --no-checkout --depth 1 --no-single-branch --config "advice.detachedHead=false" "https://github.com/libsdl-org/SDL.git" "sdl2-src"
    WORKING_DIRECTORY "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps"
    RESULT_VARIABLE error_code
    )
  math(EXPR number_of_tries "${number_of_tries} + 1")
endwhile()
if(number_of_tries GREATER 1)
  message(STATUS "Had to git clone more than once:
          ${number_of_tries} times.")
endif()
if(error_code)
  message(FATAL_ERROR "Failed to clone repository: 'https://github.com/libsdl-org/SDL.git'")
endif()

execute_process(
  COMMAND "/usr/bin/git"  checkout release-2.32.8 --
  WORKING_DIRECTORY "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-src"
  RESULT_VARIABLE error_code
  )
if(error_code)
  message(FATAL_ERROR "Failed to checkout tag: 'release-2.32.8'")
endif()

set(init_submodules TRUE)
if(init_submodules)
  execute_process(
    COMMAND "/usr/bin/git"  submodule update --recursive --init 
    WORKING_DIRECTORY "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-src"
    RESULT_VARIABLE error_code
    )
endif()
if(error_code)
  message(FATAL_ERROR "Failed to update submodules in: '/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-src'")
endif()

# Complete success, update the script-last-run stamp file:
#
execute_process(
  COMMAND ${CMAKE_COMMAND} -E copy
    "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitinfo.txt"
    "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitclone-lastrun.txt"
  RESULT_VARIABLE error_code
  )
if(error_code)
  message(FATAL_ERROR "Failed to copy script-last-run stamp file: '/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/x86/_deps/sdl2-subbuild/sdl2-populate-prefix/src/sdl2-populate-stamp/sdl2-populate-gitclone-lastrun.txt'")
endif()

