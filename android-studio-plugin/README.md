# Android Studio DistBuild Plugin

## Overview

This plugin integrates distbuild directly into Android Studio, allowing you to use distributed compilation for your Android projects without leaving the IDE.

## Features

### 1. **Automatic Project Detection**
- Plugin automatically detects when you open an Android project
- Scans for `build.gradle` with Android plugins
- Identifies all Android modules in the project

### 2. **IDE Integration**
- **Build Menu**: Adds distbuild options to Build menu
- **Toolbar Buttons**: Quick access to distbuild commands
- **Status Bar**: Shows distbuild coordinator status
- **Build Variants**: Supports debug/release build types

### 3. **Real-time Status**
- **Worker Monitoring**: See connected workers in real-time
- **Build Progress**: Visual progress bar for distributed builds
- **Error Reporting**: Android-specific error messages in IDE

## Installation

### From Plugin Repository
1. Open Android Studio
2. Go to `File > Settings > Plugins`
3. Search for "DistBuild"
4. Click Install

### Manual Installation
1. Download `distbuild-android-studio-plugin.zip`
2. Go to `File > Settings > Plugins`
3. Click gear icon > "Install Plugin from Disk..."
4. Select the downloaded file

## Usage

### Quick Start

1. **Open Android Project**: Plugin auto-detects and configures
2. **Start Coordinator**: Click "Start DistBuild" in toolbar
3. **Build Project**: Use `Build > Build with DistBuild` or `Ctrl+Shift+B`
4. **Monitor Progress**: Watch build progress in status bar

### Build Commands

#### Menu Integration
```
Build > Build with DistBuild (Ctrl+Shift+B)
Build > Rebuild with DistBuild
Build > Clean with DistBuild
Build > Build APK with DistBuild
Build > Generate Signed APK with DistBuild
```

#### Toolbar Buttons
- ![Start] Start DistBuild Coordinator
- ![Build] Build Project
- ![Debug] Build Debug APK
- ![Release] Build Release APK
- ![Status] Show Worker Status

### Configuration

#### Project Settings
```
File > Settings > DistBuild
```

**Coordinator Settings:**
- Host: localhost (default)
- Port: 8080 (default)
- Auto-start: Yes (default)

**Worker Settings:**
- Max workers: Auto (default)
- Worker threads: 4 (default)
- Specialization: android-compilation

**Build Settings:**
- Default build type: debug
- Cache enabled: Yes
- Parallel modules: Yes

#### Module-specific Settings
Right-click module > `DistBuild Settings`:
- Enable distributed compilation: Yes/No
- Build type: debug/release
- Additional compiler options

## How It Works

### 1. **Project Detection Flow**
```
1. Plugin detects Android project opening
2. Scans for build.gradle files
3. Identifies Android modules
4. Reads project configuration
5. Shows distbuild options in IDE
```

### 2. **Build Integration**
```
1. User clicks "Build with DistBuild"
2. Plugin starts coordinator (if not running)
3. Auto-connects workers
4. Distributes Java compilation
5. Handles Android-specific tasks locally
6. Shows results in IDE
```

### 3. **File System Integration**
```
Android Studio Project Structure:
app/
  src/main/java/        # Java/Kotlin files (distributed)
  src/main/res/         # Resources (local)
  build.gradle          # Module config (read by plugin)
  
Plugin detects:
- Project root: Where .idea/ folder is
- Modules: From settings.gradle
- Build files: build.gradle in each module
- SDK location: local.properties or ANDROID_HOME
```

## Advanced Features

### 1. **Build Variants Support**
```
Product Flavors:
- free, paid (build types)
- debug, release (build variants)
  
Plugin automatically:
- Detects all build variants
- Shows in Build Variants dropdown
- Supports distributed compilation for all variants
```

### 2. **Multi-module Projects**
```
Project Structure:
:app              (Android application)
:core             (Android library)
:network          (Java library)
:common           (Java library)

Plugin handles:
- Dependency graph analysis
- Parallel compilation
- Module-specific optimization
```

### 3. **Cache Integration**
```
Cache Strategy:
- Java compilation: Distributed cache
- Android resources: Local cache
- Gradle dependencies: Shared cache
- Native libraries: Worker-specific cache
```

## Troubleshooting

### Common Issues

#### "DistBuild not detected"
- Solution: Check plugin is enabled in Settings > Plugins
- Verify: Plugin shows in Help > About

#### "Coordinator not starting"
- Solution: Check port 8080 is available
- Verify: `distbuild doctor` in terminal

#### "Workers not connecting"
- Solution: Check network connectivity
- Verify: `distbuild worker list` in terminal

#### "Build fails with Android errors"
- Solution: Check ANDROID_HOME environment variable
- Verify: `distbuild doctor` shows Android SDK

### Debug Mode
Enable debug logging:
```
Help > Edit Custom Properties
distbuild.debug=true
distbuild.verbose=true
```

## API Reference

### Plugin Actions
```java
// Start coordinator
DistBuildAction.startCoordinator()

// Build project
DistBuildAction.buildProject(BuildType.DEBUG)

// Get status
DistBuildStatus.getStatus()
```

### Event Listeners
```java
// Build started
DistBuildListener.onBuildStarted(BuildEvent event)

// Build completed
DistBuildListener.onBuildCompleted(BuildResult result)

// Worker connected
DistBuildListener.onWorkerConnected(WorkerInfo worker)
```

## Development

### Building the Plugin
```bash
./gradlew buildPlugin
```

### Testing
```bash
./gradlew testPlugin
```

### Debugging
```bash
./gradlew runIde
```

## Contributing

1. Fork the repository
2. Create feature branch
3. Make changes
4. Add tests
5. Submit pull request

## License

Apache License 2.0 - see LICENSE file for details
