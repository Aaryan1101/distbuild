# 📦 Installation Scripts for DistBuild CLI

## Windows Installation

### Automated Installation (Windows)
```batch
@echo off
echo Installing DistBuild CLI...

REM Check if running as administrator
net session >nul 2>&1
if %errorLevel% == 0 (
    echo Running with administrator privileges
) else (
    echo Please run this script as administrator
    pause
    exit /b 1
)

REM Create installation directory
if not exist "C:\Program Files\DistBuild" mkdir "C:\Program Files\DistBuild"

REM Copy JAR file
copy "cli\build\libs\distbuild.jar" "C:\Program Files\DistBuild\" >nul
if %errorLevel% neq 0 (
    echo Failed to copy JAR file
    pause
    exit /b 1
)

REM Create wrapper script
echo @echo off > "C:\Program Files\DistBuild\distbuild.bat"
echo java -jar "C:\Program Files\DistBuild\distbuild.jar" %%* >> "C:\Program Files\DistBuild\distbuild.bat"

REM Add to system PATH (simplified)
setx PATH "%PATH%;C:\Program Files\DistBuild" >nul

REM Create system-wide wrapper
echo @echo off > "C:\Windows\System32\distbuild.bat"
echo java -jar "C:\Program Files\DistBuild\distbuild.jar" %%* >> "C:\Windows\System32\distbuild.bat"

echo.
echo Installation complete!
echo Testing installation...
distbuild --version

if %errorLevel% == 0 (
    echo.
    echo SUCCESS: DistBuild CLI installed successfully!
    echo You can now use 'distbuild' from anywhere.
) else (
    echo.
    echo ERROR: Installation verification failed
)

pause
```

## Linux/macOS Installation

### Automated Installation (Linux/macOS)
```bash
#!/bin/bash

echo "Installing DistBuild CLI..."

# Check if running as root
if [[ $EUID -ne 0 ]]; then
   echo "This script must be run as root (use sudo)"
   exit 1
fi

# Create installation directory
mkdir -p /opt/distbuild

# Copy JAR file
cp cli/build/libs/distbuild.jar /opt/distbuild/
if [ $? -ne 0 ]; then
    echo "Failed to copy JAR file"
    exit 1
fi

# Set permissions
chmod 644 /opt/distbuild/distbuild.jar

# Create wrapper script
cat > /usr/local/bin/distbuild << 'EOF'
#!/bin/bash
exec java -jar /opt/distbuild/distbuild.jar "$@"
EOF

# Make executable
chmod +x /usr/local/bin/distbuild

# Create symlink for convenience
ln -sf /usr/local/bin/distbuild /usr/bin/distbuild

echo ""
echo "Installation complete!"
echo "Testing installation..."
distbuild --version

if [ $? -eq 0 ]; then
    echo ""
    echo "SUCCESS: DistBuild CLI installed successfully!"
    echo "You can now use 'distbuild' from anywhere."
else
    echo ""
    echo "ERROR: Installation verification failed"
    exit 1
fi
```

## Docker Deployment

### Dockerfile
```dockerfile
FROM openjdk:17-jre-slim

# Install curl for health checks
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Create app directory
WORKDIR /app

# Copy CLI JAR
COPY cli/build/libs/distbuild.jar /app/distbuild.jar

# Create data directories
RUN mkdir -p /app/cache /app/logs

# Environment variables
ENV DISTBUILD_CACHE_DIR=/app/cache
ENV DISTBUILD_LOG_DIR=/app/logs

# Expose ports
EXPOSE 8080 8081

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:8081/health || exit 1

# Default command
ENTRYPOINT ["java", "-jar", "/app/distbuild.jar"]
```

### Docker Compose
```yaml
version: '3.8'

services:
  coordinator:
    build: .
    command: ["coordinator", "start"]
    ports:
      - "8080:8080"   # gRPC port
      - "8081:8081"   # HTTP management port
    volumes:
      - ./cache:/app/cache
      - ./logs:/app/logs
    environment:
      - DISTBUILD_CACHE_DIR=/app/cache
      - DISTBUILD_LOG_DIR=/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8081/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 40s

  worker:
    build: .
    command: ["worker", "join", "--coordinator", "coordinator"]
    depends_on:
      coordinator:
        condition: service_healthy
    volumes:
      - ./cache:/app/cache
      - ./logs:/app/logs
    environment:
      - DISTBUILD_CACHE_DIR=/app/cache
      - DISTBUILD_LOG_DIR=/app/logs
    deploy:
      replicas: 2  # Run 2 worker instances
```

## Kubernetes Deployment

### Coordinator Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: distbuild-coordinator
spec:
  replicas: 1
  selector:
    matchLabels:
      app: distbuild-coordinator
  template:
    metadata:
      labels:
        app: distbuild-coordinator
    spec:
      containers:
      - name: coordinator
        image: distbuild:latest
        command: ["distbuild", "coordinator", "start"]
        ports:
        - containerPort: 8080
          name: grpc
        - containerPort: 8081
          name: http-mgmt
        env:
        - name: DISTBUILD_CACHE_DIR
          value: "/cache"
        volumeMounts:
        - name: cache-storage
          mountPath: /cache
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /health
            port: 8081
          initialDelaySeconds: 5
          periodSeconds: 5
      volumes:
      - name: cache-storage
        persistentVolumeClaim:
          claimName: distbuild-cache-pvc

---
apiVersion: v1
kind: Service
metadata:
  name: distbuild-coordinator-service
spec:
  selector:
    app: distbuild-coordinator
  ports:
  - name: grpc
    port: 8080
    targetPort: 8080
  - name: http-mgmt
    port: 8081
    targetPort: 8081
  type: LoadBalancer

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: distbuild-cache-pvc
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
```

### Worker Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: distbuild-worker
spec:
  replicas: 3  # Scale workers as needed
  selector:
    matchLabels:
      app: distbuild-worker
  template:
    metadata:
      labels:
        app: distbuild-worker
    spec:
      containers:
      - name: worker
        image: distbuild:latest
        command: ["distbuild", "worker", "join", "--coordinator", "distbuild-coordinator-service"]
        env:
        - name: DISTBUILD_CACHE_DIR
          value: "/cache"
        volumeMounts:
        - name: cache-storage
          mountPath: /cache
        resources:
          requests:
            memory: "1Gi"
            cpu: "1000m"
          limits:
            memory: "2Gi"
            cpu: "2000m"
      volumes:
      - name: cache-storage
        persistentVolumeClaim:
          claimName: distbuild-cache-pvc
```

## Systemd Service (Linux)

### Coordinator Service
```ini
[Unit]
Description=DistBuild Coordinator
After=network.target

[Service]
Type=simple
User=distbuild
Group=distbuild
WorkingDirectory=/opt/distbuild
ExecStart=/usr/bin/java -jar /opt/distbuild/distbuild.jar coordinator start
ExecStop=/bin/kill -15 $MAINPID
Restart=always
RestartSec=10
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk
Environment=DISTBUILD_CACHE_DIR=/var/cache/distbuild

[Install]
WantedBy=multi-user.target
```

### Worker Service
```ini
[Unit]
Description=DistBuild Worker
After=network.target

[Service]
Type=simple
User=distbuild
Group=distbuild
WorkingDirectory=/opt/distbuild
ExecStart=/usr/bin/java -jar /opt/distbuild/distbuild.jar worker join
ExecStop=/bin/kill -15 $MAINPID
Restart=always
RestartSec=10
Environment=JAVA_HOME=/usr/lib/jvm/java-17-openjdk
Environment=DISTBUILD_CACHE_DIR=/var/cache/distbuild

[Install]
WantedBy=multi-user.target
```

## Installation Verification

### Test Script
```bash
#!/bin/bash

echo "Testing DistBuild CLI Installation..."

# Test basic functionality
echo "1. Testing CLI help..."
distbuild --help
if [ $? -eq 0 ]; then
    echo "✅ CLI help works"
else
    echo "❌ CLI help failed"
    exit 1
fi

# Test configuration
echo "2. Testing configuration..."
mkdir -p /tmp/distbuild-test
cd /tmp/distbuild-test
distbuild init
if [ -f "distbuild.yaml" ]; then
    echo "✅ Configuration creation works"
else
    echo "❌ Configuration creation failed"
    exit 1
fi

# Test cache operations
echo "3. Testing cache operations..."
distbuild cache stats
if [ $? -eq 0 ]; then
    echo "✅ Cache stats work"
else
    echo "❌ Cache stats failed"
    exit 1
fi

# Test completion generation
echo "4. Testing completion generation..."
distbuild generate-completion
if [ -d "completion" ]; then
    echo "✅ Completion generation works"
else
    echo "❌ Completion generation failed"
    exit 1
fi

echo ""
echo "🎉 All tests passed! DistBuild CLI is properly installed."
echo ""
echo "Quick start:"
echo "  distbuild coordinator start  # Start coordinator"
echo "  distbuild worker join        # Start worker"
echo "  distbuild status            # Check status"

# Cleanup
cd /
rm -rf /tmp/distbuild-test
```

## Usage Examples

### Development Team Setup
```bash
# 1. Each developer installs CLI
./install.sh  # or install.bat on Windows

# 2. Team lead starts coordinator
distbuild coordinator start --port 8080

# 3. Developers start workers
distbuild worker join --coordinator team-lead-machine:8080

# 4. Monitor build system
distbuild status --host team-lead-machine --port 8081
```

### CI/CD Pipeline Integration
```yaml
# GitHub Actions example
- name: Setup DistBuild
  run: |
    wget https://releases.example.com/distbuild.jar
    chmod +x distbuild.jar
    
- name: Start Coordinator
  run: |
    ./distbuild.jar coordinator start &
    sleep 10
    
- name: Build Project
  run: |
    ./distbuild.jar worker join --max-tasks 4 &
    # Build commands here
    
- name: Check Status
  run: |
    ./distbuild.jar status
```

These installation scripts provide comprehensive deployment options for any environment!
