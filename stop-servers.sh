#!/bin/bash

# Pokemon TCG Tracker - Stop Servers Script
echo "🛑 Stopping Pokemon TCG Tracker Servers"
echo "=================================="

# Check if PIDs file exists
if [ -f .server_pids ]; then
    PIDS=$(cat .server_pids)
    echo "📋 Found server PIDs: $PIDS"
    
    # Kill the processes
    for PID in $PIDS; do
        if kill -0 $PID 2>/dev/null; then
            echo "🔪 Stopping process $PID"
            kill $PID
        else
            echo "⚠️  Process $PID not running"
        fi
    done
    
    # Clean up PID file
    rm .server_pids
    echo "🧹 Cleaned up PID file"
else
    echo "⚠️  No server PIDs found - killing by name"
    # Fallback: kill by process name
    pkill -f "gradle.*bootRun" || echo "❌ No backend process found"
    pkill -f "next dev" || echo "❌ No frontend process found"
fi

echo "✅ Server shutdown complete"