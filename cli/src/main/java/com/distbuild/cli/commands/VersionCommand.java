package com.distbuild.cli.commands;

import com.distbuild.common.build.BuildInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "version",
    description = "Show distbuild version information"
)
public class VersionCommand implements Runnable {
    
    @Override
    public void run() {
        System.out.println(BuildInfo.getFullVersion());
        System.out.println("Distributed Java build system");
        System.out.println();
        System.out.println("Build information:");
        System.out.println("  " + BuildInfo.getBuildInfo().replace("\n", "\n  "));
        System.out.println("  Current Java: " + System.getProperty("java.version"));
        System.out.println("  JVM: " + System.getProperty("java.vm.name"));
        System.out.println("  OS: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println();
        System.out.println("Components:");
        System.out.println("  Coordinator: Distributed build coordination");
        System.out.println("  Worker: Build task execution");
        System.out.println("  Cache: Local disk caching");
        System.out.println("  CLI: Command-line interface");
        System.out.println("  Android: APK build support");
        System.out.println();
        System.out.println("Load balancing strategies:");
        System.out.println("  ROUND_ROBIN");
        System.out.println("  LEAST_LOADED");
        System.out.println("  WEIGHTED_RESPONSE_TIME");
        System.out.println("  CAPABILITY_BASED");
        System.out.println();
        System.out.println("For help: distbuild --help");
    }
}
