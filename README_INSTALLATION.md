# Installation

### Quick Install (Recommended)

#### Linux / macOS
```bash
curl -fsSL https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.sh | bash
```

#### Windows (PowerShell)
```powershell
irm https://raw.githubusercontent.com/Aaryan1101/distbuild/main/installer/install.ps1 | iex
```

### Package Managers

#### Scoop (Windows)
```powershell
scoop bucket add distbuild https://github.com/Aaryan1101/scoop-distbuild
scoop install distbuild
```

#### apt (Ubuntu / Debian)
```bash
curl -fsSL https://Aaryan1101.github.io/distbuild/gpg.key | sudo gpg --dearmor -o /etc/apt/keyrings/distbuild.gpg
echo 'deb [signed-by=/etc/apt/keyrings/distbuild.gpg] https://Aaryan1101.github.io/distbuild stable main' | sudo tee /etc/apt/sources.list.d/distbuild.list
sudo apt update && sudo apt install distbuild
```

#### winget (Windows 11)
```powershell
winget install Aaryan1101.distbuild
```

### Manual Installation

#### Prerequisites
- Java 17 or higher
- Redis (recommended for optimal performance)

#### Steps
1. Download the latest release:
   ```bash
   wget https://github.com/Aaryan1101/distbuild/releases/download/v1.0.0/distbuild-1.0.0.jar
   ```

2. Create installation directory:
   ```bash
   sudo mkdir -p /opt/distbuild
   sudo mv distbuild-1.0.0.jar /opt/distbuild/distbuild.jar
   ```

3. Create launcher script:
   ```bash
   sudo tee /opt/distbuild/bin/distbuild > /dev/null <<'EOF'
   #!/bin/bash
   exec java -Xmx512m -jar /opt/distbuild/distbuild.jar "$@"
   EOF
   sudo chmod +x /opt/distbuild/bin/distbuild
   ```

4. Add to PATH:
   ```bash
   echo 'export PATH="/opt/distbuild/bin:$PATH"' >> ~/.bashrc
   source ~/.bashrc
   ```

### Verification

After installation, verify everything is working:

```bash
distbuild version
```

Should show:
```
distbuild 1.0.0
Distributed Java build system

Build information:
  Version: 1.0.0
  Java Version: 17
  Build Date: 2026-04-22
  Build Author: distbuild-team
  Current Java: 17.0.17
  JVM: OpenJDK 64-Bit Server VM
  OS: Linux 6.5.0
```

Run diagnostics:
```bash
distbuild doctor
```

### Troubleshooting

#### Java Issues
```bash
# Check Java version
java -version

# Install Java 17 (Ubuntu/Debian)
sudo apt update && sudo apt install openjdk-17-jdk

# Install Java 17 (macOS)
brew install openjdk@17

# Install Java 17 (Windows with Chocolatey)
choco install openjdk
```

#### Permission Issues
```bash
# Fix permissions on Linux/macOS
sudo chown -R $USER:$USER /opt/distbuild
sudo chmod +x /opt/distbuild/bin/distbuild
```

#### PATH Issues
```bash
# Check if distbuild is in PATH
which distbuild

# Add to PATH temporarily
export PATH="/opt/distbuild/bin:$PATH"

# Add to PATH permanently
echo 'export PATH="/opt/distbuild/bin:$PATH"' >> ~/.bashrc
source ~/.bashrc
```

### Uninstallation

#### Linux/macOS
```bash
# Remove installation directory
sudo rm -rf /opt/distbuild

# Remove from PATH
sed -i '/opt\/distbuild\/bin/d' ~/.bashrc
source ~/.bashrc
```

#### Windows (Scoop)
```powershell
scoop uninstall distbuild
```

#### Windows (apt)
```bash
sudo apt remove distbuild
sudo rm /etc/apt/sources.list.d/distbuild.list
sudo apt update
```

### Next Steps

After successful installation:

1. **Start the coordinator:**
   ```bash
   distbuild coordinator start
   ```

2. **Start a worker:**
   ```bash
   distbuild worker start
   ```

3. **Check status:**
   ```bash
   distbuild status
   ```

4. **Build your first project:**
   ```bash
   # For Java projects
   distbuild build --type DEBUG
   
   # For Android projects
   distbuild android build --type debug
   ```

For more help:
```bash
distbuild --help
distbuild doctor
```

### Support

- **Documentation**: https://github.com/YOUR_USERNAME/distbuild
- **Issues**: https://github.com/YOUR_USERNAME/distbuild/issues
- **Discussions**: https://github.com/YOUR_USERNAME/distbuild/discussions
