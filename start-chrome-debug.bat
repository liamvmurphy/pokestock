@echo off
echo Starting Chrome with remote debugging for Facebook Marketplace...
echo Profile: C:\Users\Liam\chrome-debug-profile
echo Debug Port: 9230
echo.
echo You can now log into Facebook in this browser window and it will stay logged in.
echo The application will connect to this Chrome instance.
echo.

"C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9230 --user-data-dir="C:\Users\Liam\chrome-debug-profile"