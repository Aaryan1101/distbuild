package com.distbuild.cli;

import com.distbuild.cli.commands.*;
import com.distbuild.common.build.BuildInfo;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Spec;

/**
 * Main CLI entry point for distributed build system
 */
@Command(
    name = "distbuild",
    description = "Distributed Java build system — coordinator, worker, and cache management.",
    subcommands = {
        BuildCommand.class,
        AndroidBuildCommand.class,
        WorkerCommand.class,
        CoordinatorCommand.class,
        StatusCommand.class,
        CacheCommand.class,
        InitCommand.class,
        ConfigCommand.class,
        LogsCommand.class,
        DoctorCommand.class,
        VersionCommand.class,
        GenerateCompletionCommand.class
    },
    mixinStandardHelpOptions = true,
    version = "distbuild 1.0.0"
)
public class DistBuildCli implements Runnable {
    
    @Spec
    CommandLine.Model.CommandSpec spec;
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new DistBuildCli()).execute(args);
        System.exit(exitCode);
    }
    
    @Override
    public void run() {
        // No subcommand given — print help
        spec.commandLine().usage(System.out);
    }
}
