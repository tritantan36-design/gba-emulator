@echo off
setlocal
call "C:\Program Files (x86)\Microsoft Visual Studio\18\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
if errorlevel 1 exit /b 1
set "TASK_CMAKE=D:\GPT\tools\android-sdk\cmake\3.22.1\bin\cmake.exe"
"%TASK_CMAKE%" -S core-mgba/src/test/cpp -B core-mgba/build/asan-tests -G Ninja -DCMAKE_BUILD_TYPE=Debug -DGBA_HOST_ASAN=ON -DCMAKE_MAKE_PROGRAM=D:/GPT/tools/android-sdk/cmake/3.22.1/bin/ninja.exe
if errorlevel 1 exit /b 1
"%TASK_CMAKE%" --build core-mgba/build/asan-tests --parallel 4
if errorlevel 1 exit /b 1
"D:\GPT\tools\android-sdk\cmake\3.22.1\bin\ctest.exe" --test-dir core-mgba/build/asan-tests --output-on-failure
exit /b %errorlevel%
