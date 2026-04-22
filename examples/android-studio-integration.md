# Android Studio Integration - How It Works

## **Real-World Usage Example**

### **Scenario: You're developing an Android app in Android Studio**

#### **Step 1: Open Your Android Project**
```
C:\Users\YourName\AndroidStudioProjects\MyCoolApp\
my-cool-app\
  app\
    src\main\java\com\example\myapp\
    src\main\res\
    build.gradle
  core\
    src\main\java\com\example\core\
    build.gradle
  build.gradle
  settings.gradle
  local.properties
```

#### **Step 2: DistBuild Auto-Detects Project**

When you open the project, the Android Studio plugin automatically:

1. **Scans for Android markers**
   - Finds `build.gradle` with `com.android.application`
   - Detects `settings.gradle` with module declarations
   - Identifies `app/` and `core/` as Android modules

2. **Reads project structure**
   ```
   Project Root: C:\Users\YourName\AndroidStudioProjects\MyCoolApp\my-cool-app
   Android Modules: [:app, :core]
   Java Modules: [:network, :common]
   Build Types: debug, release
   ```

3. **Shows in IDE**
   - Status bar: "DistBuild: Ready (1 worker)"
   - Build menu: "Build with DistBuild" appears
   - Toolbar: DistBuild buttons appear

#### **Step 3: Start Building with DistBuild**

**Option A: Use Android Studio UI**
```
Build > Build with DistBuild (Ctrl+Shift+B)
```

**Option B: Use Terminal in Android Studio**
```bash
# Android Studio Terminal automatically in project root
distbuild android build --type debug
```

#### **Step 4: What Happens Behind the Scenes**

##### **Phase 1: Project Analysis**
```
1. Plugin reads settings.gradle:
   include ':app', ':core', ':network', ':common'

2. Analyzes each build.gradle:
   :app -> com.android.application (Android)
   :core -> com.android.library (Android)
   :network -> java-library (Java)
   :common -> java-library (Java)

3. Builds dependency graph:
   :app depends on [:core, :network]
   :core depends on [:common]
   :network depends on [:common]
```

##### **Phase 2: Distributed Compilation**
```
Layer 0 (no dependencies):
  :common -> Java compilation -> Worker 1
  :network -> Java compilation -> Worker 2

Layer 1 (depends on :common):
  :core -> Java compilation -> Worker 1
  (Android resources handled locally)

Layer 2 (depends on :core, :network):
  :app -> Java compilation -> Worker 2
  (Android resources handled locally)
```

##### **Phase 3: Android-Specific Processing (Local)**
```
For :core module:
  - Compile Java (done by worker)
  - Process Android resources (local)
  - Generate R.java (local)
  - Create AAR (local)

For :app module:
  - Compile Java (done by worker)
  - Process Android resources (local)
  - Merge manifests (local)
  - Generate R.java (local)
  - Dex compilation (local)
  - APK packaging (local)
  - Sign APK (local if release)
```

#### **Step 5: Results in Android Studio**

##### **Build Output Window**
```
DistBuild: Starting distributed build...
DistBuild: Found 4 modules (2 Android, 2 Java)
DistBuild: Connected 2 workers
DistBuild: Layer 0: :common, :network (parallel)
DistBuild: Layer 1: :core (Java distributed, resources local)
DistBuild: Layer 2: :app (Java distributed, resources local)
DistBuild: Java compilation completed in 45s (vs 120s local)
DistBuild: Android processing completed in 30s
DistBuild: APK generated: app/build/outputs/apk/debug/app-debug.apk
BUILD SUCCESSFUL in 75s
```

##### **File Explorer**
```
app/build/
  outputs/
    apk/
      debug/
        app-debug.apk  <- Your final APK
    intermediate/
      dex/
        debug/         <- Dex files (local)
      merged_res/
        debug/         <- Merged resources (local)
```

## **How the Tool Knows Where Things Are**

### **1. **Project Detection Algorithm**
```java
// AndroidStudioPlugin.java
public void onProjectOpened(Project project) {
    Path projectRoot = project.getBasePath();
    
    // Search for Android project markers
    while (projectRoot != null) {
        if (isAndroidProject(projectRoot)) {
            configureDistBuild(projectRoot);
            return;
        }
        projectRoot = projectRoot.getParent();
    }
}

private boolean isAndroidProject(Path dir) {
    return Files.exists(dir.resolve("build.gradle")) &&
           containsAndroidPlugin(dir.resolve("build.gradle"));
}
```

### **2. **File System Integration**
```
Android Studio Project Structure:
my-cool-app/
  .idea/                    <- IDE config (plugin reads this)
  app/                      <- Main app module
    src/main/java/         <- Java/Kotlin source (distributed)
    src/main/res/          <- Resources (local processing)
    build.gradle           <- Module config (plugin reads)
  core/                    <- Library module
    src/main/java/         <- Java/Kotlin source (distributed)
    src/main/res/          <- Resources (local processing)
    build.gradle           <- Module config (plugin reads)
  build.gradle             <- Project config (plugin reads)
  settings.gradle          <- Module list (plugin reads)
  local.properties         <- SDK path (plugin reads/creates)
```

### **3. **Build Configuration Detection**
```java
// From build.gradle files
android {
    compileSdk 34
    defaultConfig {
        applicationId "com.example.myapp"
    }
    buildTypes {
        debug {
            applicationIdSuffix ".debug"
        }
        release {
            minifyEnabled true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}

// Plugin extracts:
- compileSdk: 34
- applicationId: com.example.myapp
- buildTypes: [debug, release]
- minifyEnabled: true (release)
```

### **4. **SDK Path Resolution**
```java
// Priority order:
1. Environment variable ANDROID_HOME
2. local.properties file
3. Android Studio default location
4. User prompt

// local.properties example
sdk.dir=C:\\Users\\YourName\\AppData\\Local\\Android\\Sdk
```

## **Practical Commands in Android Studio**

### **Terminal Integration**
```bash
# From Android Studio Terminal (automatically in project root)
distbuild android detect        # Shows project info
distbuild android build         # Build debug APK
distbuild android build --type release  # Build release APK
distbuild android setup         # Setup Android SDK
```

### **Menu Integration**
```
Build Menu:
  Build Project(s)                    [Ctrl+F9] <- Standard Android Studio
  Build with DistBuild                [Ctrl+Shift+B] <- Our addition
  Rebuild Project                     [Ctrl+Shift+F9] <- Standard
  Rebuild with DistBuild              [Ctrl+Shift+R] <- Our addition
  Clean Project                       <- Standard
  Clean with DistBuild                <- Our addition
  
Android Menu:
  Build APK(s)                        <- Standard
  Build APK(s) with DistBuild         <- Our addition
  Generate Signed Bundle/APK...       <- Standard
  Generate Signed Bundle/APK with DistBuild <- Our addition
```

### **Toolbar Integration**
```
Standard Toolbar: [Build] [Run] [Debug]
DistBuild Toolbar: [Start Coordinator] [Build DistBuild] [Status] [Workers]
```

## **Error Handling in IDE**

### **Common Issues and IDE Integration**

#### **"Android SDK not found"**
```
IDE Shows:
  - Dialog: "Android SDK not detected"
  - Button: "Setup Android SDK"
  - Command: distbuild android setup --auto-install
```

#### **"Workers not connected"**
```
IDE Shows:
  - Status bar: "DistBuild: No workers"
  - Button: "Start Worker"
  - Command: distbuild worker start
```

#### **"Build failed"**
```
IDE Shows:
  - Build window with Android-specific errors
  - Links to problematic files
  - Suggested fixes from distbuild doctor
```

## **Performance Benefits**

### **Before DistBuild (Local Only)**
```
Total build time: 3 minutes 45 seconds
- :common compilation: 45s
- :network compilation: 30s  
- :core compilation: 60s
- :app compilation: 90s
- Android processing: 30s
```

### **After DistBuild (Distributed)**
```
Total build time: 1 minute 30 seconds
- :common compilation: 15s (Worker 1)
- :network compilation: 10s (Worker 2) [parallel]
- :core compilation: 20s (Worker 1)
- :app compilation: 25s (Worker 2)
- Android processing: 30s (local)
```

**Speed improvement: 2.5x faster!**

This integration makes distbuild seamlessly work within Android Studio, automatically detecting your project structure and providing distributed compilation without changing your workflow.
