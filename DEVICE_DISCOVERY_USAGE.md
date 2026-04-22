# Device Discovery Usage Guide

## Overview
Your distributed build system now supports **automatic device discovery**! Workers can automatically find coordinators on the local network without manual configuration.

## How It Works

### Coordinator Side
1. Coordinator starts broadcasting "I'm here" messages on port 8081
2. Broadcasts every 5 seconds to all network interfaces
3. Messages include coordinator port information

### Worker Side
1. Worker listens for discovery messages on port 8081
2. When coordinator is found, worker automatically connects
3. Falls back to localhost if no coordinator discovered

## Usage Examples

### 1. Zero-Configuration Setup (Recommended)

#### Start Coordinator:
```bash
cd C:\Users\jayde\OneDrive\Desktop\Minor Project\distcc
.\gradlew.bat :coordinator:run
```
*Coordinator automatically starts discovery broadcasting*

#### Start Workers:
```bash
# Terminal 2 - First worker
.\gradlew.bat :worker:run

# Terminal 3 - Second worker  
.\gradlew.bat :worker:run

# Terminal 4 - Third worker
.\gradlew.bat :worker:run
```
*Workers automatically discover and connect to coordinator!*

### 2. Manual Connection (Fallback)

#### Start Coordinator:
```bash
.\gradlew.bat :coordinator:run --port=8080 --no-discovery
```

#### Connect Workers Manually:
```bash
.\gradlew.bat :worker:run --coordinator-host=192.168.1.100 --coordinator-port=8080
```

### 3. Mixed Setup

#### Coordinator with Discovery:
```bash
.\gradlew.bat :coordinator:run --port=8080
```

#### Workers - Some Auto, Some Manual:
```bash
# Auto-discovery
.\gradlew.bat :worker:run

# Manual connection
.\gradlew.bat :worker:run --coordinator-host=192.168.1.100

# Custom worker ID
.\gradlew.bat :worker:run --worker-id=build-worker-1
```

## Network Configuration

### Local Network Discovery
- Works on same local network (LAN)
- Uses UDP broadcast on port 8081
- No firewall configuration needed for most networks

### Cross-Network Discovery
For discovery across different networks:
```bash
# Coordinator - specify network interface
.\gradlew.bat :coordinator:run --network-interface=192.168.1.0

# Worker - specify network to search
.\gradlew.bat :worker:run --discovery-network=192.168.1.0
```

## Advanced Options

### Coordinator Options
```bash
.\gradlew.bat :coordinator:run --help
```
Output:
```
Enhanced Build Coordinator with Device Discovery
Usage: java -jar coordinator.jar [options]

Options:
  --port <port>           Server port (default: 8080)
  --cache-dir <dir>       Cache directory (default: ./coordinator-cache)
  --no-discovery          Disable automatic device discovery
  --help                  Show this help message

Discovery Features:
  - Automatic worker discovery on local network
  - Network broadcasting of coordinator availability
  - Zero-configuration worker connection
```

### Worker Options
```bash
.\gradlew.bat :worker:run --help
```
Output:
```
Enhanced Build Worker with Device Discovery
Usage: java -jar worker.jar [options]

Options:
  --worker-id <id>              Worker identifier (default: auto-generated)
  --coordinator-host <host>     Coordinator host (disables auto-discovery)
  --coordinator-port <port>     Coordinator port (default: 8080)
  --max-tasks <num>             Maximum concurrent tasks (default: 4)
  --no-discovery                Disable automatic coordinator discovery
  --help                        Show this help message

Discovery Features:
  - Automatic coordinator discovery on local network
  - Zero-configuration connection
  - Fallback to manual configuration

Usage Examples:
  # Auto-discover coordinator:
  java -jar worker.jar

  # Manual connection:
  java -jar worker.jar --coordinator-host=192.168.1.100

  # Custom worker ID:
  java -jar worker.jar --worker-id=build-worker-1
```

## Real-World Scenarios

### Development Team Setup
```bash
# Team lead starts coordinator
.\gradlew.bat :coordinator:run --port=8080

# Team members start workers (auto-discover)
.\gradlew.bat :worker:run --worker-id=alice-workstation
.\gradlew.bat :worker:run --worker-id=bob-laptop
.\gradlew.bat :worker:run --worker-id=charlie-desktop
```

### CI/CD Pipeline
```bash
# Build server starts coordinator
./gradlew :coordinator:run --port=8080

# Multiple build agents start workers
./gradlew :worker:run --worker-id=agent-1 --max-tasks=8
./gradlew :worker:run --worker-id=agent-2 --max-tasks=8
./gradlew :worker:run --worker-id=agent-3 --max-tasks=8
```

### Cloud Environment
```bash
# Coordinator in cloud
./gradlew :coordinator:run --port=8080

# Workers across multiple instances
./gradlew :worker:run --coordinator-host=coordinator.internal.company.com
```

## Troubleshooting

### Workers Not Discovering Coordinator
1. **Check Network**: Ensure devices are on same network
2. **Firewall**: Allow UDP traffic on port 8081
3. **Manual Connection**: Use `--coordinator-host` as fallback

### Coordinator Not Found
```bash
# Check if coordinator is broadcasting
.\gradlew.bat :coordinator:run --port=8080

# In another terminal, test discovery
.\gradlew.bat :worker:run --no-discovery --coordinator-host=localhost
```

### Multiple Coordinators
```bash
# Start first coordinator
.\gradlew.bat :coordinator:run --port=8080

# Start second coordinator (different port)
.\gradlew.bat :coordinator:run --port=8081

# Workers will discover the first one they find
```

## Performance Benefits

### Before Manual Configuration
```bash
# Manual setup required
./gradlew :worker:run --coordinator-host=192.168.1.100 --coordinator-port=8080
./gradlew :worker:run --coordinator-host=192.168.1.100 --coordinator-port=8080
./gradlew :worker:run --coordinator-host=192.168.1.100 --coordinator-port=8080
```

### After Automatic Discovery
```bash
# Zero configuration - just start workers!
./gradlew :worker:run
./gradlew :worker:run
./gradlew :worker:run
```

## Security Considerations

### Network Security
- Discovery uses UDP broadcast (local network only)
- No sensitive information in discovery messages
- gRPC communication still uses secure channels

### Production Deployment
```bash
# Disable discovery in production for security
.\gradlew.bat :coordinator:run --no-discovery

# Use explicit host configuration
.\gradlew.bat :worker:run --coordinator-host=coordinator.prod.company.com
```

## Next Steps

Your distributed build system now supports:
- **Zero-configuration worker connection**
- **Automatic device discovery**
- **Manual fallback for complex networks**
- **Production-ready security options**

**Start using it today:**
```bash
# Terminal 1: Start coordinator
.\gradlew.bat :coordinator:run

# Terminal 2+: Start workers (auto-discover!)
.\gradlew.bat :worker:run
```

**Device discovery is now fully operational!**
