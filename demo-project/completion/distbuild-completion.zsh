#compdef distbuild

_distbuild() {
    local -a commands
    commands=(
        'coordinator:Manage coordinator node'
        'worker:Manage worker agent on this machine'
        'status:Show all connected workers and system status'
        'cache:Manage build cache'
        'init:Interactive setup â€” writes distbuild.yaml'
        'generate-completion:Generate shell completion scripts'
    )

    if [[ CURRENT -eq 1 ]]; then
        _describe 'command' commands
        return
    fi

    case $words[1] in
        coordinator)
            local coordinator_commands=(
                'start:Start coordinator node'
                'stop:Gracefully stop coordinator'
                'status:Show coordinator health'
            )
            if [[ CURRENT -eq 2 ]]; then
                _describe 'coordinator command' coordinator_commands
            fi
            ;;
        worker)
            local worker_commands=(
                'join:Start worker and register with coordinator'
                'leave:Drain tasks and deregister this worker'
                'status:Show this worker\'s status'
            )
            if [[ CURRENT -eq 2 ]]; then
                _describe 'worker command' worker_commands
            fi
            ;;
    esac
}

_distbuild
