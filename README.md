# Distbuild

A distributed build system for Java projects that enables parallel compilation across multiple machines.

## Features

- **Parallel Compilation**: Distribute build tasks across multiple worker machines
- **Automatic Device Discovery**: Find and connect to available build workers automatically
- **CLI Interface**: Simple command-line interface for managing distributed builds
- **Gradle Plugin**: Integrate distributed builds into existing Gradle projects
- **Cross-Platform**: Works on Windows, macOS, and Linux

## Quick Start

### Scoop Installation (Windows) - Recommended

**First, install Scoop (if not already installed):**
```powershell
irm get.scoop.sh | iex
```

**Then install distbuild:**
```powershell
scoop bucket add distbuild https://github.com/Aaryan1101/scoop-distbuild
scoop install distbuild
```

### Manual Installation - Always Works

**Download and run directly:**
```powershell
# Download JAR
Invoke-WebRequest -Uri 'https://github.com/Aaryan1101/distbuild/releases/download/v1.1.4/distbuild-1.1.4.jar' -OutFile 'distbuild.jar'

# Run it
java -jar distbuild.jar --help
```

**Or download from:** [Releases](https://github.com/Aaryan1101/distbuild/releases)

## Setup and Usage

### First Time Setup
```bash
# 1. Check system requirements
distbuild doctor

# 2. Start the coordinator (in terminal 1)
distbuild coordinator start

# 3. Start a worker (in terminal 2)
distbuild worker start

# 4. Check status
distbuild status
```

### Basic Commands
```bash
# Start a distributed build
distbuild build

# List available workers
distbuild list-workers

# Add a worker manually
distbuild add-worker <worker-ip>

# Check system health
distbuild doctor

# View logs
distbuild logs
```

### Troubleshooting
If `distbuild doctor` shows issues:

1. **"Cannot connect to coordinator"** - Start the coordinator:
   ```bash
   distbuild coordinator start
   ```

2. **"Cache directory does not exist"** - Create directories:
   ```bash
   mkdir distbuild-cache
   mkdir logs
   ```

3. **"Java version incompatible"** - Install Java 17+:
   ```powershell
   # Windows
   winget install Oracle.JavaRuntimeEnvironment
   # or download from https://adoptium.net/
   ```

4. **"'distbuild' command not recognized"** - Add Scoop to PATH:
   ```powershell
   # Add Scoop shims to PATH manually
   [Environment]::SetEnvironmentVariable("PATH", $env:PATH + ";C:\Users\jayde\scoop\shims", "User")
   
   # Then restart PowerShell and try again
   distbuild --help
   ```

### Advanced Usage
```bash
# Interactive setup
distbuild init

# Configure settings
distbuild config set coordinator.port 8080
distbuild config set worker.memory 2048

# Clear cache
distbuild cache clear

# Generate shell completion
distbuild generate-completion
```

### Gradle Plugin
Add to your `build.gradle`:
```groovy
plugins {
    id "com.distbuild.gradle" version "1.1.4"
}

distbuild {
    workers = ["worker1.example.com", "worker2.example.com"]
}
```

## Architecture

- **Coordinator**: Manages build tasks and worker coordination
- **Worker**: Executes build tasks on remote machines
- **CLI**: Command-line interface for user interaction
- **Gradle Plugin**: Integrates with existing Gradle projects

## Documentation

- [Installation Guide](https://github.com/Aaryan1101/distbuild/wiki/Installation)
- [User Guide](https://github.com/Aaryan1101/distbuild/wiki/User-Guide)
- [API Reference](https://github.com/Aaryan1101/distbuild/wiki/API)

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Releases

- **v1.1.4** - Latest stable release with GitHub Actions automation
- See [Releases](https://github.com/Aaryan1101/distbuild/releases) for all versions
