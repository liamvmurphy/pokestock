#!/bin/bash

# Pokemon TCG Tracker - Network Startup Script
echo "🚀 Starting Pokemon TCG Tracker for Network Access"
echo "=================================="

# Get local IP address
LOCAL_IP=$(ifconfig | grep "inet " | grep -v 127.0.0.1 | head -1 | awk '{print $2}')
echo "📡 Local IP Address: $LOCAL_IP"

# Start backend (Spring Boot)
echo "🔧 Starting Backend Server..."
cd backend
./gradlew bootRun &
BACKEND_PID=$!
echo "✅ Backend running on http://$LOCAL_IP:8080 (PID: $BACKEND_PID)"

# Wait for backend to start
echo "⏳ Waiting for backend to initialize..."
sleep 10

# Start frontend (Next.js)
echo "🎨 Starting Frontend Server..."
cd ../frontend
npm run dev &
FRONTEND_PID=$!
echo "✅ Frontend running on http://$LOCAL_IP:3000 (PID: $FRONTEND_PID)"

echo ""
echo "🌐 NETWORK ACCESS READY!"
echo "=================================="
echo "📱 Access from any device on your network:"
echo "   Frontend: http://$LOCAL_IP:3000"
echo "   Backend:  http://$LOCAL_IP:8080"
echo ""
echo "🛑 To stop servers:"
echo "   kill $BACKEND_PID $FRONTEND_PID"
echo ""
echo "📋 Server PIDs saved to .server_pids"

# Save PIDs for easy cleanup
echo "$BACKEND_PID $FRONTEND_PID" > .server_pids

# Keep script running
echo "🔄 Servers running... Press Ctrl+C to stop all services"
trap "echo '🛑 Stopping servers...'; kill $BACKEND_PID $FRONTEND_PID; exit" INT
wait