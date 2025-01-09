#!/bin/zsh

lowercase() {
    for item in "$1"/*; do
        if [ -d "$item" ]; then
            # If it's a directory, recurse into it
            lowercase "$item"
        elif [ -f "$item" ]; then
            # If it's a file, rename it to lowercase
            dir="${item:h}"       # Get the directory part
            base="${item:t}"      # Get the filename part
            lower="${base:l}"     # Convert filename to lowercase
            if [ "$base" != "$lower" ]; then
                mv "$item" "$dir/$lower"
                echo "Renamed: $item -> $dir/$lower"
            fi
        fi
    done
}

# Start from the current directory or a specified path
lowercase "${1:-.}"
