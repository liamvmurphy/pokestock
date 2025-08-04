#!/bin/bash
echo "Starting Chrome with remote debugging for Facebook Marketplace..."
echo "Profile: /Users/lmurphy/chrome-debug-profile"
echo "Debug Port: 9230"
echo
echo "You can now log into Facebook in this browser window and it will stay logged in."
echo "The application will connect to this Chrome instance."
echo

"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome" --remote-debugging-port=9230 --user-data-dir="/Users/lmurphy/chrome-debug-profile"