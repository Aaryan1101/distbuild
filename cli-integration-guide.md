# CLI Integration Guide — Distributed Java Build System

> Addendum to the main project guide. Adds a `distbuild` CLI tool so developers run
> `distbuild coordinator start` instead of long `java -jar ... --flag value` commands.

---

## Table of Contents

1. [Overview](#1-overview)
2. [New Module: cli/](#2-new-module-cli)
3. [Command Structure](#3-command-structure)
4. [Implementation](#4-implementation)
5. [Configuration File](#5-configuration-file)
6. [Packaging & Distribution](#6-packaging--distribution)
7. [Shell Completion](#7-shell-completion)
8. [Integration with Existing Modules](#8-integration-with-existing-modules)
9. [Development Checklist](#9-development-checklist)

---

## 1. Overview

The current startup flow requires memorizing flags:

```bash
# Before — hard to remember, error-prone
java -jar coordinator/build/libs/coordinator-all.jar \
  --grpc-port 9090 \
  --http-port 8080 \
  --cache-url redis://localhost:6379
```

After adding the CLI module, every operation becomes a single readable command:

```bash
distbuild coordinator start          # Start coordinator with config from distbuild.yaml
distbuild worker join --host laptop  # Register this machine as a worker
distbuild status                     # Show all connected workers + build queue
distbuild cache stats                # Hit rate, entries, bytes saved
distbuild init                       # Interactive first-time setup wizard
```

**Framework:** [Picocli 4.7.5](https://picocli.info/) — integrates with Gradle, generates shell
completion scripts, supports subcommands, and has zero runtime dependencies beyond its own JAR.

---

## 2. New Module: cli/

Add `cli` to `settings.gradle.kts`:

```kotlin
include("proto", "common", "dag", "cache", "coordinator", "worker", "gradle-plugin",
        "cli", "integration-tests")
```

### File Structure

```
cli/
├── build.gradle.kts
└── src/main/java/com/distbuild/cli/
    ├── DistBuildCli.java            # Root @Command — entry point
    ├── CoordinatorCommand.java      # distbuild coordinator start | stop | status
    ├── WorkerCommand.java           # distbuild worker join | leave | status
    ├── StatusCommand.java           # distbuild status — global view
    ├── CacheCommand.java            # distbuild cache stats | clear | warm
    └── InitCommand.java             # distbuild init — interactive setup wizard
```

### build.gradle.kts

```kotlin
plugins {
    id("application")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    implementation(project(":coordinator"))
    implementation(project(":worker"))
    implementation(project(":cache"))
    implementation(project(":common"))
    implementation("info.picocli:picocli:4.7.5")
    annotationProcessor("info.picocli:picocli-codegen:4.7.5")
}

application {
    mainClass.set("com.distbuild.cli.DistBuildCli")
}

tasks.shadowJar {
    archiveBaseName.set("distbuild")
    archiveClassifier.set("")
    archiveVersion.set("")
    mergeServiceFiles()
}
```

Build the fat JAR with: `./gradlew :cli:shadowJar`
Output: `cli/build/libs/distbuild.jar`

---

## 3. Command Structure

```
distbuild
├── coordinator
│   ├── start       Start coordinator (reads distbuild.yaml; flags override)
│   ├── stop        Graceful shutdown via HTTP POST /shutdown
│   └── status      Show coordinator health, worker count, active build
├── worker
│   ├── join        Start this machine as a worker and register with coordinator
│   ├── leave       Drain in-flight tasks and deregister gracefully
│   └── status      Show this worker's load, active tasks, JAR cache size
├── status          Global view — all workers, current build queue, cache hit rate
├── cache
│   ├── stats       Hit rate, total entries, bytes stored, TTL settings
│   ├── clear       Flush all entries (Redis FLUSHDB scoped to distbuild: keys)
│   └── warm        Pre-populate cache from a previous build's artifacts
├── init            Interactive wizard — writes distbuild.yaml
└── build           (Phase 2) Trigger a distributed build without Gradle plugin
```

---

## 4. Implementation

### 4.1 Root Command — DistBuildCli.java

```java
@Command(
    name = "distbuild",
    description = "Distributed Java build system — coordinator, worker, and cache management.",
    subcommands = {
        CoordinatorCommand.class,
        WorkerCommand.class,
        StatusCommand.class,
        CacheCommand.class,
        InitCommand.class,
        GenerateCompletion.class   // built-in from picocli-shell-jline3
    },
    mixinStandardHelpOptions = true,
    version = "0.1.0"
)
public class DistBuildCli implements Runnable {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DistBuildCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        // No subcommand given — print help
        new CommandLine(this).usage(System.out);
    }
}
```

### 4.2 Coordinator Subcommand — CoordinatorCommand.java

```java
@Command(
    name = "coordinator",
    description = "Manage the coordinator node.",
    subcommands = {
        CoordinatorCommand.Start.class,
        CoordinatorCommand.Stop.class,
        CoordinatorCommand.Status.class
    }
)
public class CoordinatorCommand implements Runnable {

    @Override
    public void run() {
        new CommandLine(this).usage(System.out);
    }

    @Command(name = "start", description = "Start the coordinator node.")
    static class Start implements Runnable {

        @Option(names = {"--config", "-c"}, description = "Path to distbuild.yaml",
                defaultValue = "distbuild.yaml")
        File configFile;

        @Option(names = "--grpc-port", description = "gRPC listen port (default: 9090)")
        Integer grpcPort;

        @Option(names = "--http-port", description = "HTTP listen port (default: 8080)")
        Integer httpPort;

        @Option(names = "--cache-url", description = "Redis URL (default: redis://localhost:6379)")
        String cacheUrl;

        @Override
        public void run() {
            // 1. Load distbuild.yaml
            DistBuildConfig config = Config.load(configFile);
            // 2. Apply CLI overrides (flags beat config file)
            if (grpcPort != null) config.coordinator.grpcPort = grpcPort;
            if (httpPort != null) config.coordinator.httpPort = httpPort;
            if (cacheUrl  != null) config.cache.url = cacheUrl;
            // 3. Delegate to existing CoordinatorMain logic
            CoordinatorMain.start(config);
        }
    }

    @Command(name = "stop", description = "Gracefully stop the coordinator.")
    static class Stop implements Runnable {

        @Option(names = "--host", defaultValue = "localhost")
        String host;

        @Option(names = "--port", defaultValue = "8080")
        int port;

        @Override
        public void run() {
            String url = "http://" + host + ":" + port + "/shutdown";
            System.out.println("Sending shutdown signal to " + url);
            // HTTP POST to /shutdown endpoint (already planned in Phase 4)
            HttpClient.post(url);
        }
    }

    @Command(name = "status", description = "Show coordinator health and connected workers.")
    static class Status implements Runnable {
        // Calls GET /health and pretty-prints JSON
        @Override
        public void run() { StatusCommand.printCoordinatorStatus("localhost", 8080); }
    }
}
```

### 4.3 Worker Subcommand — WorkerCommand.java

```java
@Command(name = "worker", description = "Manage the worker agent on this machine.",
         subcommands = { WorkerCommand.Join.class, WorkerCommand.Leave.class })
public class WorkerCommand implements Runnable {

    @Override
    public void run() { new CommandLine(this).usage(System.out); }

    @Command(name = "join", description = "Start worker and register with coordinator.")
    static class Join implements Runnable {

        @Option(names = {"--coordinator", "-c"}, required = true,
                description = "Coordinator host (e.g. 192.168.1.10 or laptop.local)")
        String coordinatorHost;

        @Option(names = "--coordinator-port", defaultValue = "9090")
        int coordinatorPort;

        @Option(names = "--cache-url", defaultValue = "redis://localhost:6379")
        String cacheUrl;

        @Option(names = "--grpc-port", defaultValue = "9091")
        int grpcPort;

        @Option(names = "--threads",
                description = "Parallel compile tasks (default: CPU cores - 1)")
        Integer threads;

        @Override
        public void run() {
            WorkerConfig config = WorkerConfig.builder()
                .coordinatorHost(coordinatorHost)
                .coordinatorPort(coordinatorPort)
                .cacheUrl(cacheUrl)
                .grpcPort(grpcPort)
                .threads(threads != null ? threads : Runtime.getRuntime().availableProcessors() - 1)
                .build();
            WorkerMain.start(config);
        }
    }

    @Command(name = "leave", description = "Drain tasks and deregister this worker.")
    static class Leave implements Runnable {
        @Override
        public void run() { WorkerMain.shutdown(); }
    }
}
```

### 4.4 Status Command — StatusCommand.java

Calls the coordinator's `/health` HTTP endpoint and renders a formatted table.

```java
@Command(name = "status", description = "Show all connected workers and build queue.")
public class StatusCommand implements Runnable {

    @Option(names = {"--host"}, defaultValue = "localhost")
    String host;

    @Option(names = {"--port"}, defaultValue = "8080")
    int port;

    @Override
    public void run() {
        printCoordinatorStatus(host, port);
    }

    static void printCoordinatorStatus(String host, int port) {
        // GET http://host:port/health
        // Expected JSON: { workers: [{id, host, tasks, healthy}], cacheHitRate, activeBuild }
        HealthResponse health = HttpClient.get("http://" + host + ":" + port + "/health",
                                               HealthResponse.class);
        System.out.printf("Coordinator:  %s:%d  [%s]%n", host, port,
                          health.status.toUpperCase());
        System.out.printf("Cache hit rate (session): %.1f%%%n", health.cacheHitRate * 100);
        System.out.println();
        System.out.printf("%-20s %-16s %-8s %s%n", "WORKER ID", "HOST", "TASKS", "STATUS");
        System.out.println("-".repeat(60));
        for (WorkerInfo w : health.workers) {
            System.out.printf("%-20s %-16s %-8d %s%n",
                              w.id, w.host, w.activeTasks, w.healthy ? "HEALTHY" : "OFFLINE");
        }
    }
}
```

Sample output:

```
Coordinator:  localhost:8080  [HEALTHY]
Cache hit rate (session): 73.4%

WORKER ID            HOST             TASKS    STATUS
------------------------------------------------------------
alice-laptop         192.168.1.12     2        HEALTHY
ci-server-01         192.168.1.20     0        HEALTHY
bob-macbook          192.168.1.31     1        HEALTHY
```

### 4.5 Init Command — InitCommand.java

Interactive wizard that writes `distbuild.yaml`. New team members run this once.

```java
@Command(name = "init", description = "Interactive setup — writes distbuild.yaml.")
public class InitCommand implements Runnable {

    @Override
    public void run() {
        Scanner in = new Scanner(System.in);
        System.out.println("=== distbuild init ===");
        System.out.println("This writes distbuild.yaml in the current directory.\n");

        String coordinatorHost = prompt(in, "Coordinator host", "localhost");
        String grpcPort        = prompt(in, "Coordinator gRPC port", "9090");
        String httpPort        = prompt(in, "Coordinator HTTP port", "8080");
        String cacheUrl        = prompt(in, "Redis URL", "redis://localhost:6379");
        String timeout         = prompt(in, "Worker timeout (seconds)", "120");

        String yaml = String.format("""
            coordinator:
              host: %s
              grpc-port: %s
              http-port: %s
            cache:
              url: %s
              ttl-days: 7
            workers:
              timeout-seconds: %s
            """, coordinatorHost, grpcPort, httpPort, cacheUrl, timeout);

        Files.writeString(Path.of("distbuild.yaml"), yaml);
        System.out.println("\ndistbuild.yaml written. Start the coordinator with:");
        System.out.println("  distbuild coordinator start");
    }

    private String prompt(Scanner in, String label, String defaultValue) {
        System.out.printf("  %s [%s]: ", label, defaultValue);
        String input = in.nextLine().trim();
        return input.isEmpty() ? defaultValue : input;
    }
}
```

---

## 5. Configuration File

`distbuild init` writes this file. All CLI flags override values here.

```yaml
# distbuild.yaml
coordinator:
  host: localhost
  grpc-port: 9090
  http-port: 8080

cache:
  url: redis://localhost:6379
  ttl-days: 7

workers:
  timeout-seconds: 120

# Optional: static worker list (skip for mDNS auto-discovery)
# worker-pool:
#   - host: 192.168.1.12
#     port: 9091
#   - host: 192.168.1.20
#     port: 9091
```

`Config.java` in `common/` already reads YAML. Extend it to merge CLI overrides:

```java
public static DistBuildConfig load(File configFile) {
    if (configFile.exists()) {
        return yaml.loadAs(new FileReader(configFile), DistBuildConfig.class);
    }
    return DistBuildConfig.defaults(); // sensible defaults if no config file present
}
```

---

## 6. Packaging & Distribution

### Option A — Shell Wrapper (Ready Now)

After `./gradlew :cli:shadowJar`, install with one script:

```bash
# install.sh
JAR_PATH="$(pwd)/cli/build/libs/distbuild.jar"
WRAPPER="/usr/local/bin/distbuild"

cat > "$WRAPPER" <<EOF
#!/bin/bash
exec java -jar "$JAR_PATH" "\$@"
EOF
chmod +x "$WRAPPER"
echo "Installed: distbuild --help"
```

Teammates clone the repo and run `./install.sh` once.

### Option B — GraalVM Native Image (Phase 4 Polish)

Produces a single binary with no JVM dependency. Starts in ~30ms.

```kotlin
// cli/build.gradle.kts
plugins {
    id("org.graalvm.buildtools.native") version "0.9.28"
}

graalvmNative {
    binaries.named("main") {
        imageName.set("distbuild")
        mainClass.set("com.distbuild.cli.DistBuildCli")
        buildArgs.add("--no-fallback")
        buildArgs.add("-H:+ReportExceptionStackTraces")
    }
}
```

Build: `./gradlew :cli:nativeCompile`
Output: `cli/build/native/nativeCompile/distbuild` — copy to `/usr/local/bin/`.

> **Note:** GraalVM native image requires GraalVM JDK 21+ (`sdk install java 21.0.3-graal`).
> Picocli has full native-image support via its annotation processor — no reflection config needed.

---

## 7. Shell Completion

Picocli generates completion scripts automatically.

```bash
# Bash
distbuild generate-completion bash > ~/.distbuild-completion.sh
echo "source ~/.distbuild-completion.sh" >> ~/.bashrc

# Zsh
distbuild generate-completion zsh > ~/.distbuild-completion.zsh
echo "source ~/.distbuild-completion.zsh" >> ~/.zshrc

# Fish
distbuild generate-completion fish > ~/.config/fish/completions/distbuild.fish
```

After sourcing, tab completion works for all commands and flags:

```
$ distbuild co<TAB>
coordinator

$ distbuild coordinator <TAB>
start   stop   status

$ distbuild worker join --<TAB>
--coordinator   --coordinator-port   --cache-url   --grpc-port   --threads
```

Add `generate-completion` to the root command:

```java
import picocli.AutoComplete.GenerateCompletion;

@Command(subcommands = { ..., GenerateCompletion.class })
public class DistBuildCli { ... }
```

---

## 8. Integration with Existing Modules

No changes to existing module logic are required. The CLI is a thin dispatch layer.

| CLI Command | Delegates To |
|---|---|
| `distbuild coordinator start` | `CoordinatorMain.start(config)` |
| `distbuild coordinator stop` | HTTP POST to existing `/shutdown` endpoint |
| `distbuild coordinator status` | HTTP GET to existing `/health` endpoint |
| `distbuild worker join` | `WorkerMain.start(config)` |
| `distbuild worker leave` | `WorkerMain.shutdown()` |
| `distbuild status` | HTTP GET `/health` + formatted table |
| `distbuild cache stats` | Calls `BuildCache.stats()` interface method (add this) |
| `distbuild cache clear` | Calls `BuildCache.invalidateAll()` (scoped to `distbuild:v1:*` keys) |

### One Addition to `BuildCache.java`

Add two methods to the existing interface to support the cache subcommands:

```java
public interface BuildCache {
    Optional<CacheEntry> get(CacheKey key);
    void put(CacheKey key, CacheEntry entry);
    void invalidate(CacheKey key);

    // New — needed by distbuild cache stats/clear
    CacheStats stats();         // returns hit count, miss count, entry count, bytes used
    void invalidateAll();       // flushes all distbuild:v1:* keys in Redis
}
```

---

## 9. Development Checklist

This fits cleanly into **Phase 4** of the existing roadmap (Week 8).

- [ ] Add `cli/` module to `settings.gradle.kts`
- [ ] Create `cli/build.gradle.kts` with Picocli + Shadow JAR config
- [ ] Implement `DistBuildCli.java` (root command)
- [ ] Implement `CoordinatorCommand.java` (start / stop / status)
- [ ] Implement `WorkerCommand.java` (join / leave)
- [ ] Implement `StatusCommand.java` (global status table)
- [ ] Implement `CacheCommand.java` (stats / clear)
- [ ] Implement `InitCommand.java` (interactive wizard)
- [ ] Add `stats()` and `invalidateAll()` to `BuildCache` interface + both impls
- [ ] Write `install.sh` shell wrapper script
- [ ] Add `generate-completion` subcommand
- [ ] Test: `distbuild coordinator start` matches output of manual `java -jar` invocation
- [ ] Test: `distbuild init` writes valid `distbuild.yaml`
- [ ] Test: `distbuild status` formats correctly when 0, 1, and 3 workers are connected
- [ ] (Optional) GraalVM native image build + verify cold-start time

---

*CLI Integration Guide — Distributed Java Build System | Addendum v1.0*
