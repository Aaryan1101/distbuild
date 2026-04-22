# Bash completion for distbuild
_distbuild() {
    local cur prev words cword
    _init_completion || return

    # Main commands
    if [[ $cword -eq 1 ]]; then
        COMPREPLY=($(compgen -W "coordinator worker status cache init generate-completion --help --version" -- "$cur"))
        return
    fi

    # Coordinator subcommands
    if [[ ${words[1]} == "coordinator" ]]; then
        if [[ $cword -eq 2 ]]; then
            COMPREPLY=($(compgen -W "start stop status" -- "$cur"))
            return
        fi
    fi

    # Worker subcommands
    if [[ ${words[1]} == "worker" ]]; then
        if [[ $cword -eq 2 ]]; then
            COMPREPLY=($(compgen -W "join leave status" -- "$cur"))
            return
        fi
    fi

    # Cache subcommands
    if [[ ${words[1]} == "cache" ]]; then
        if [[ $cword -eq 2 ]]; then
            COMPREPLY=($(compgen -W "stats clear" -- "$cur"))
            return
        fi
    fi

    # Options for start command
    if [[ ${words[2]} == "start" ]]; then
        case "$prev" in
            --config|-c)
                COMPREPLY=($(compgen -f -- "$cur"))
                ;;
            *)
                COMPREPLY=($(compgen -W "--config --port --cache-dir --no-discovery --help" -- "$cur"))
                ;;
        esac
    fi

    # Options for join command
    if [[ ${words[2]} == "join" ]]; then
        case "$prev" in
            --config|-c)
                COMPREPLY=($(compgen -f -- "$cur"))
                ;;
            *)
                COMPREPLY=($(compgen -W "--config --coordinator --coordinator-port --worker-id --max-tasks --no-discovery --help" -- "$cur"))
                ;;
        esac
    fi
}

complete -F _distbuild distbuild
