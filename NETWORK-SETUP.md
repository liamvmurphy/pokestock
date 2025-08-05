# 🌐 Pokemon TCG Tracker - Network Access Setup

This guide shows you how to access your Pokemon TCG Tracker from any device on your local network (phones, tablets, other computers).

## 🚀 Quick Start

### Option 1: Automatic Startup Script
```bash
# Make scripts executable (first time only)
chmod +x start-network.sh stop-servers.sh

# Start both servers for network access
./start-network.sh
```

### Option 2: Manual Startup

#### Backend (Spring Boot)
```bash
cd backend
./gradlew bootRun
```

#### Frontend (Next.js)
```bash
cd frontend
npm run dev
```

## 📱 Access URLs

Your local IP address: **192.168.86.162**

- **Frontend (Main App)**: http://192.168.86.162:3000
- **Backend API**: http://192.168.86.162:8080

## 🔧 Configuration Details

### Backend Changes
- Added `server.address=0.0.0.0` to `application.properties`
- Now accepts connections from any device on the network

### Frontend Changes
- Added `-H 0.0.0.0` flag to Next.js dev command
- Created API utility with environment-based URLs
- Added network info display component

### Environment Variables
The frontend uses these environment variables (set in `.env.local`):

```bash
NEXT_PUBLIC_API_BASE_URL=http://192.168.86.162:8080
NEXT_PUBLIC_HOST_IP=192.168.86.162
```

## 📱 Testing Network Access

### From Your Phone/Tablet:
1. Connect to the same WiFi network
2. Open browser and navigate to: `http://192.168.86.162:3000`
3. You should see the Pokemon TCG Tracker interface

### From Another Computer:
1. Ensure it's on the same network
2. Navigate to: `http://192.168.86.162:3000`

## 🛑 Stopping Services

### Using the Script:
```bash
./stop-servers.sh
```

### Manual Stop:
- Press `Ctrl+C` in each terminal running the services
- Or kill processes by PID if running in background

## 🔧 Troubleshooting

### Can't Access from Other Devices?

1. **Check Firewall**: Your Mac's firewall might be blocking connections
   - Go to System Preferences → Security & Privacy → Firewall
   - Allow incoming connections for Java and Node.js

2. **Verify Network**: Ensure all devices are on the same WiFi network

3. **Check IP Address**: Your IP might have changed
   ```bash
   ifconfig | grep "inet " | grep -v 127.0.0.1
   ```

4. **Port Issues**: Make sure ports 3000 and 8080 aren't blocked

### Services Won't Start?

1. **Port Already in Use**:
   ```bash
   # Kill existing processes
   pkill -f "gradle.*bootRun"
   pkill -f "next dev"
   ```

2. **Check Java/Node Versions**:
   ```bash
   java -version
   node -version
   ```

## 🎯 Features Available on Network

All features work exactly the same when accessed remotely:

- ✅ Marketplace Scanner
- ✅ CSV Analysis with Claude AI
- ✅ Comprehensive Market Intelligence Reports
- ✅ Real-time marketplace monitoring
- ✅ Google Sheets integration

## 🔒 Security Notes

- This setup is for **local network only**
- Don't expose these ports to the internet without proper security
- The current setup has basic authentication (`admin/changeme`)
- For production use, implement proper security measures

## 📊 Network Info Display

When running in network mode, you'll see a green network info card at the top of the interface showing:
- Frontend URL for sharing
- Backend URL for API access
- Current host IP address
- Copy buttons for easy sharing