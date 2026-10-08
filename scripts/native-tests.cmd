@echo off
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
set "TASK_CMAKE=D:\GPT\tools\android-sdk\cmake\3.22.1\bin\cmake.exe"
set "TASK_NINJA=D:/GPT/tools/android-sdk/cmake/3.22.1/bin/ninja.exe"
"%TASK_CMAKE%" -S audio/src/test/cpp -B audio/build/native-tests -G Ninja -DCMAKE_BUILD_TYPE=Debug -DCMAKE_MAKE_PROGRAM="%TASK_NINJA%"
if errorlevel 1 exit /b 1
"%TASK_CMAKE%" --build audio/build/native-tests
if errorlevel 1 exit /b 1
"D:\GPT\tools\android-sdk\cmake\3.22.1\bin\ctest.exe" --test-dir audio/build/native-tests --output-on-failure
if errorlevel 1 exit /b 1
"%TASK_CMAKE%" -S core-mgba/src/test/cpp -B core-mgba/build/host-tests -G Ninja -DCMAKE_BUILD_TYPE=Debug -DCMAKE_MAKE_PROGRAM="%TASK_NINJA%"
if errorlevel 1 exit /b 1
"%TASK_CMAKE%" --build core-mgba/build/host-tests --parallel 4
if errorlevel 1 exit /b 1
"D:\GPT\tools\android-sdk\cmake\3.22.1\bin\ctest.exe" --test-dir core-mgba/build/host-tests --output-on-failure
exit /b %errorlevel%
