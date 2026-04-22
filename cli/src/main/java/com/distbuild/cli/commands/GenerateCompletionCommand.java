package com.distbuild.cli.commands;

import picocli.AutoComplete;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generate shell completion scripts
 */
@Command(
    name = "generate-completion",
    description = "Generate shell completion scripts"
)
public class GenerateCompletionCommand implements Runnable {
    
    @Option(
        names = {"-o", "--output"},
        description = "Output directory for completion scripts",
        defaultValue = "completion"
    )
    File outputDir;
    
    @Override
    public void run() {
        try {
            // Create output directory
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            System.out.println("Generating shell completion scripts...");
            
            // Generate bash completion
            Path bashScript = outputDir.toPath().resolve("distbuild-completion.bash");
            String bashCompletion = generateBashCompletion();
            Files.writeString(bashScript, bashCompletion);
            System.out.println("✓ Bash completion: " + bashScript);
            
            // Generate zsh completion
            Path zshScript = outputDir.toPath().resolve("distbuild-completion.zsh");
            String zshCompletion = generateZshCompletion();
            Files.writeString(zshScript, zshCompletion);
            System.out.println("✓ Zsh completion: " + zshScript);
            
            // Generate fish completion
            Path fishScript = outputDir.toPath().resolve("distbuild-completion.fish");
            String fishCompletion = generateFishCompletion();
            Files.writeString(fishScript, fishCompletion);
            System.out.println("✓ Fish completion: " + fishScript);
            
            System.out.println();
            System.out.println("Installation:");
            System.out.println("  Bash:  source " + bashScript + " >> ~/.bashrc");
            System.out.println("  Zsh:   source " + zshScript + " >> ~/.zshrc");
            System.out.println("  Fish:   cp " + fishScript + " ~/.config/fish/completions/");
            
        } catch (IOException e) {
            System.err.println("Failed to generate completion scripts: " + e.getMessage());
            System.exit(1);
        }
    }
    
    private String generateBashCompletion() {
        return "# Bash completion for distbuild\n" +
               "_distbuild() {\n" +
               "    local cur prev words cword\n" +
               "    _init_completion || return\n" +
               "\n" +
               "    # Main commands\n" +
               "    if [[ $cword -eq 1 ]]; then\n" +
               "        COMPREPLY=($(compgen -W \"coordinator worker status cache init generate-completion --help --version\" -- \"$cur\"))\n" +
               "        return\n" +
               "    fi\n" +
               "\n" +
               "    # Coordinator subcommands\n" +
               "    if [[ ${words[1]} == \"coordinator\" ]]; then\n" +
               "        if [[ $cword -eq 2 ]]; then\n" +
               "            COMPREPLY=($(compgen -W \"start stop status\" -- \"$cur\"))\n" +
               "            return\n" +
               "        fi\n" +
               "    fi\n" +
               "\n" +
               "    # Worker subcommands\n" +
               "    if [[ ${words[1]} == \"worker\" ]]; then\n" +
               "        if [[ $cword -eq 2 ]]; then\n" +
               "            COMPREPLY=($(compgen -W \"join leave status\" -- \"$cur\"))\n" +
               "            return\n" +
               "        fi\n" +
               "    fi\n" +
               "\n" +
               "    # Cache subcommands\n" +
               "    if [[ ${words[1]} == \"cache\" ]]; then\n" +
               "        if [[ $cword -eq 2 ]]; then\n" +
               "            COMPREPLY=($(compgen -W \"stats clear\" -- \"$cur\"))\n" +
               "            return\n" +
               "        fi\n" +
               "    fi\n" +
               "\n" +
               "    # Options for start command\n" +
               "    if [[ ${words[2]} == \"start\" ]]; then\n" +
               "        case \"$prev\" in\n" +
               "            --config|-c)\n" +
               "                COMPREPLY=($(compgen -f -- \"$cur\"))\n" +
               "                ;;\n" +
               "            *)\n" +
               "                COMPREPLY=($(compgen -W \"--config --port --cache-dir --no-discovery --help\" -- \"$cur\"))\n" +
               "                ;;\n" +
               "        esac\n" +
               "    fi\n" +
               "\n" +
               "    # Options for join command\n" +
               "    if [[ ${words[2]} == \"join\" ]]; then\n" +
               "        case \"$prev\" in\n" +
               "            --config|-c)\n" +
               "                COMPREPLY=($(compgen -f -- \"$cur\"))\n" +
               "                ;;\n" +
               "            *)\n" +
               "                COMPREPLY=($(compgen -W \"--config --coordinator --coordinator-port --worker-id --max-tasks --no-discovery --help\" -- \"$cur\"))\n" +
               "                ;;\n" +
               "        esac\n" +
               "    fi\n" +
               "}\n" +
               "\n" +
               "complete -F _distbuild distbuild\n";
    }
    
    private String generateZshCompletion() {
        return "#compdef distbuild\n" +
               "\n" +
               "_distbuild() {\n" +
               "    local -a commands\n" +
               "    commands=(\n" +
               "        'coordinator:Manage coordinator node'\n" +
               "        'worker:Manage worker agent on this machine'\n" +
               "        'status:Show all connected workers and system status'\n" +
               "        'cache:Manage build cache'\n" +
               "        'init:Interactive setup — writes distbuild.yaml'\n" +
               "        'generate-completion:Generate shell completion scripts'\n" +
               "    )\n" +
               "\n" +
               "    if [[ CURRENT -eq 1 ]]; then\n" +
               "        _describe 'command' commands\n" +
               "        return\n" +
               "    fi\n" +
               "\n" +
               "    case $words[1] in\n" +
               "        coordinator)\n" +
               "            local coordinator_commands=(\n" +
               "                'start:Start coordinator node'\n" +
               "                'stop:Gracefully stop coordinator'\n" +
               "                'status:Show coordinator health'\n" +
               "            )\n" +
               "            if [[ CURRENT -eq 2 ]]; then\n" +
               "                _describe 'coordinator command' coordinator_commands\n" +
               "            fi\n" +
               "            ;;\n" +
               "        worker)\n" +
               "            local worker_commands=(\n" +
               "                'join:Start worker and register with coordinator'\n" +
               "                'leave:Drain tasks and deregister this worker'\n" +
               "                'status:Show this worker\\'s status'\n" +
               "            )\n" +
               "            if [[ CURRENT -eq 2 ]]; then\n" +
               "                _describe 'worker command' worker_commands\n" +
               "            fi\n" +
               "            ;;\n" +
               "    esac\n" +
               "}\n" +
               "\n" +
               "_distbuild\n";
    }
    
    private String generateFishCompletion() {
        return "# Fish completion for distbuild\n" +
               "\n" +
               "complete -c distbuild -f\n" +
               "\n" +
               "# Main commands\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a coordinator -d \"Manage coordinator node\"\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a worker -d \"Manage worker agent\"\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a status -d \"Show system status\"\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a cache -d \"Manage build cache\"\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a init -d \"Interactive setup\"\n" +
               "complete -c distbuild -n \"__fish_use_subcommand\" -a generate-completion -d \"Generate completion scripts\"\n" +
               "\n" +
               "# Coordinator subcommands\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from coordinator\" -a start -d \"Start coordinator\"\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from coordinator\" -a stop -d \"Stop coordinator\"\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from coordinator\" -a status -d \"Show status\"\n" +
               "\n" +
               "# Worker subcommands\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from worker\" -a join -d \"Join worker pool\"\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from worker\" -a leave -d \"Leave worker pool\"\n" +
               "complete -c distbuild -n \"__fish_seen_subcommand_from worker\" -a status -d \"Show worker status\"\n" +
               "\n" +
               "# Options\n" +
               "complete -c distbuild -s c -l config -r -d \"Path to distbuild.yaml\"\n" +
               "complete -c distbuild -l port -r -d \"Port number\"\n" +
               "complete -c distbuild -l cache-dir -r -d \"Cache directory\"\n" +
               "complete -c distbuild -l no-discovery -d \"Disable device discovery\"\n" +
               "complete -c distbuild -l coordinator -r -d \"Coordinator host\"\n" +
               "complete -c distbuild -l coordinator-port -r -d \"Coordinator port\"\n" +
               "complete -c distbuild -l worker-id -r -d \"Worker identifier\"\n" +
               "complete -c distbuild -l max-tasks -r -d \"Maximum concurrent tasks\"\n";
    }
}
