if (NOT EXISTS "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/arm64-v8a/install_manifest.txt")
    message(FATAL_ERROR "Cannot find install manifest: \"/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/arm64-v8a/install_manifest.txt\"")
endif(NOT EXISTS "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/arm64-v8a/install_manifest.txt")

file(READ "/Users/miguel_garcia/Documents/Proyectos/pdandroid/android/app/.cxx/Debug/5t1f1ol3/arm64-v8a/install_manifest.txt" files)
string(REGEX REPLACE "\n" ";" files "${files}")
foreach (file ${files})
    message(STATUS "Uninstalling \"$ENV{DESTDIR}${file}\"")
    execute_process(
        COMMAND /Users/miguel_garcia/Library/Android/sdk/cmake/3.22.1/bin/cmake -E remove "$ENV{DESTDIR}${file}"
        OUTPUT_VARIABLE rm_out
        RESULT_VARIABLE rm_retval
    )
    if(NOT ${rm_retval} EQUAL 0)
        message(FATAL_ERROR "Problem when removing \"$ENV{DESTDIR}${file}\"")
    endif (NOT ${rm_retval} EQUAL 0)
endforeach(file)

