# Fish completion for distbuild

complete -c distbuild -f

# Main commands
complete -c distbuild -n "__fish_use_subcommand" -a coordinator -d "Manage coordinator node"
complete -c distbuild -n "__fish_use_subcommand" -a worker -d "Manage worker agent"
complete -c distbuild -n "__fish_use_subcommand" -a status -d "Show system status"
complete -c distbuild -n "__fish_use_subcommand" -a cache -d "Manage build cache"
complete -c distbuild -n "__fish_use_subcommand" -a init -d "Interactive setup"
complete -c distbuild -n "__fish_use_subcommand" -a generate-completion -d "Generate completion scripts"

# Coordinator subcommands
complete -c distbuild -n "__fish_seen_subcommand_from coordinator" -a start -d "Start coordinator"
complete -c distbuild -n "__fish_seen_subcommand_from coordinator" -a stop -d "Stop coordinator"
complete -c distbuild -n "__fish_seen_subcommand_from coordinator" -a status -d "Show status"

# Worker subcommands
complete -c distbuild -n "__fish_seen_subcommand_from worker" -a join -d "Join worker pool"
complete -c distbuild -n "__fish_seen_subcommand_from worker" -a leave -d "Leave worker pool"
complete -c distbuild -n "__fish_seen_subcommand_from worker" -a status -d "Show worker status"

# Options
complete -c distbuild -s c -l config -r -d "Path to distbuild.yaml"
complete -c distbuild -l port -r -d "Port number"
complete -c distbuild -l cache-dir -r -d "Cache directory"
complete -c distbuild -l no-discovery -d "Disable device discovery"
complete -c distbuild -l coordinator -r -d "Coordinator host"
complete -c distbuild -l coordinator-port -r -d "Coordinator port"
complete -c distbuild -l worker-id -r -d "Worker identifier"
complete -c distbuild -l max-tasks -r -d "Maximum concurrent tasks"
