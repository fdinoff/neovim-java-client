#!/bin/bash

tmux new-session -d 'NVIM_LISTEN_ADDRESS=127.0.0.1:6666 nvim'

tmux split-window 'mvn exec:java -Dexec.mainClass="com.neovim.Neovim" 2>/dev/null'
tmux resize-pane -x 80 -y 12
tmux select-pane -t 0
tmux attach
