@echo off
setlocal EnableDelayedExpansion
title VibeOS TV — Acer Google TV (192.168.1.33) 1-Click Auto-Connect ^& Debloat
color 0B

cd /d "%~dp0"
set "TV_IP=192.168.1.33"

echo ======================================================================
echo   VibeOS TV — Acer Google TV (Android 14) 1-Click Setup
echo   Target TV IP: %TV_IP%
echo ======================================================================
echo.

:: Step 1: Check or Auto-Download Official Google ADB (No Python needed!)
set "ADB_EXE="
where adb >nul 2>nul
if %errorlevel%==0 (
    set "ADB_EXE=adb"
) else if exist "%~dp0platform-tools\adb.exe" (
    set "ADB_EXE=%~dp0platform-tools\adb.exe"
) else (
    echo [*] Aapke PC par ADB installed nahi hai.
    echo [*] Google ka official ADB Platform-Tools automatically download ho raha hai...
    powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -OutFile '%~dp0platform-tools.zip'; Expand-Archive -Path '%~dp0platform-tools.zip' -DestinationPath '%~dp0' -Force; Remove-Item '%~dp0platform-tools.zip' -Force"
    if exist "%~dp0platform-tools\adb.exe" (
        set "ADB_EXE=%~dp0platform-tools\adb.exe"
        echo [+] ADB successfully download ho gaya!
    ) else (
        echo [!] ADB download fail ho gaya. Internet connection check karein.
        pause
        exit /b 1
    )
)

echo.
echo [1/4] Starting ADB Server ^& Connecting to Acer Google TV (%TV_IP%)...
"%ADB_EXE%" start-server

:: First try standard ADB port 5555
"%ADB_EXE%" connect %TV_IP%:5555
timeout /t 2 >nul

"%ADB_EXE%" devices | findstr /C:"%TV_IP%" | findstr /C:"device" >nul
if %errorlevel%==0 (
    set "SERIAL=%TV_IP%:5555"
    goto :CONNECTED
)

echo.
echo [*] Port 5555 par direct connect nahi hua (Android 14 Wireless Debugging dynamic port use kar raha hoga).
echo ----------------------------------------------------------------------
echo TV par Settings -^> System -^> Developer Options -^> Wireless Debugging kholein:
echo  - Agar pehli baar jod rahe hain toh 'Pair device with pairing code' par click karein
echo    aur niche 'P' likh kar Enter dabayein.
echo  - Agar Wireless Debugging main screen par IP ^& Port dikh raha hai (jaise 192.168.1.33:39123),
echo    toh sirf woh 5-digit PORT number dalein:
echo ----------------------------------------------------------------------
set /p "USER_CHOICE=Yahan 'P' ya Port Number dalein (ya 5555 ke liye Enter): "

if /I "%USER_CHOICE%"=="P" (
    set /p "PAIR_PORT=TV par dikha Pairing Port dalein (e.g. 37451): "
    set /p "PAIR_CODE=TV par dikha 6-digit Pairing Code dalein: "
    "%ADB_EXE%" pair %TV_IP%:!PAIR_PORT! !PAIR_CODE!
    echo.
    set /p "CONN_PORT=Ab Wireless Debugging ke main page wala Port dalein: "
    "%ADB_EXE%" connect %TV_IP%:!CONN_PORT!
    set "SERIAL=%TV_IP%:!CONN_PORT!"
) else if not "%USER_CHOICE%"=="" (
    "%ADB_EXE%" connect %TV_IP%:%USER_CHOICE%
    set "SERIAL=%TV_IP%:%USER_CHOICE%"
) else (
    "%ADB_EXE%" connect %TV_IP%:5555
    set "SERIAL=%TV_IP%:5555"
)

echo.
echo [!] TV Screen Dekhein: Agar 'Allow debugging?' popup aaya hai toh Remote se
echo     'Always allow from this computer' tick karke 'Allow' dabayein!
pause

:CONNECTED
echo.
echo [+] Connected to %SERIAL%!
echo.
echo [2/4] Backing up installed packages to acer_tv_packages_backup.txt...
"%ADB_EXE%" -s %SERIAL% shell pm list packages -e > "%~dp0acer_tv_packages_backup.txt"

echo.
echo [3/4] Applying Apple TV-style 60fps fast animation tweaks...
"%ADB_EXE%" -s %SERIAL% shell settings put global window_animation_scale 0.5
"%ADB_EXE%" -s %SERIAL% shell settings put global transition_animation_scale 0.5
"%ADB_EXE%" -s %SERIAL% shell settings put global animator_duration_scale 0.5

echo.
echo [4/4] Removing Google TV Bloatware, Telemetry ^& Sponsor Services...
for %%P in (
    com.google.android.feedback
    com.google.android.partnersetup
    com.google.android.syncadapters.calendar
    com.google.android.syncadapters.contacts
    com.android.dreams.basic
    com.android.printspooler
    com.android.bips
    com.google.android.apps.tachyon
    com.google.android.play.games
    com.google.android.videos
) do (
    echo    [-] Disabling %%P ...
    "%ADB_EXE%" -s %SERIAL% shell pm disable-user --user 0 %%P 2>nul
)

if exist "%~dp0app-debug.apk" (
    echo.
    echo [*] Found app-debug.apk! Installing VibeOS Apple TV Launcher...
    "%ADB_EXE%" -s %SERIAL% install -r "%~dp0app-debug.apk"
)

"%ADB_EXE%" -s %SERIAL% shell pm list packages com.vibeos.tvlauncher | findstr "com.vibeos.tvlauncher" >nul
if %errorlevel%==0 (
    echo [+] Setting VibeOS Launcher as Default Home ^& Disabling Google TV Ad Launcher...
    "%ADB_EXE%" -s %SERIAL% shell cmd role add-role-holder android.app.role.HOME com.vibeos.tvlauncher
    "%ADB_EXE%" -s %SERIAL% shell cmd package set-home-activity com.vibeos.tvlauncher/.MainActivity
    "%ADB_EXE%" -s %SERIAL% shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
    "%ADB_EXE%" -s %SERIAL% shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
    "%ADB_EXE%" -s %SERIAL% shell am start -n com.vibeos.tvlauncher/.MainActivity
) else (
    echo.
    echo [i] Note: Stock launcher abhi on rakha gaya hai taaki black screen na aaye.
    echo     Jab aapka 'app-debug.apk' is folder mein aa jaye, tab is file par dobara double-click karein!
)

echo.
echo ======================================================================
echo  [SUCCESS] Aapka Acer Google TV (%TV_IP%) optimize ho chuka hai!
echo ======================================================================
pause
