#!/bin/bash
# Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
# SPDX-License-Identifier: Apache-2.0
#
# This script runs unit tests for the solution.
# Run this script from the deployment/ directory.
#
# USAGE:
#   cd deployment
#   ./run-unit-tests.sh
# -------------------------------------

set -e  # Exit on error
set -o pipefail  # Exit on error in piped commands

usage() {
  echo "$msg"
  cat <<EOF
Usage: $(basename "${BASH_SOURCE[0]}") [-h] [-v]

Available options:

-h, --help        Print this help and exit (optional)
-v, --verbose     Enable command tracing (optional)
EOF
  exit 1
}

parse_params() {
  while :; do
    case "${1-}" in
    -h | --help) usage ;;
    -v | --verbose) set -x ;;
    -?*) echo "Unknown option: $1" && exit 1 ;;
      *) break ;;
      esac
      shift
    done
}

# Function to print an error message and exit
print_error_and_exit() {
    echo "$1 Exiting."
    exit 0
}

# Function to check if a required command is available
check_command() {
    if ! command -v "$1" &>/dev/null; then
        print_error_and_exit "$1 is not available in the environment."
    fi
}

# Function to run unit tests
run_unit_tests() {
  echo "Running unit tests..."
  flutter --version
  echo ""
  flutter analyze --fatal-warnings
  echo ""
  flutter test --coverage
}

# Main script execution
main() {
    # Exit if script was not run from the deployment/ folder
    if [ "${PWD##*/}" != "deployment" ]; then
        echo "Error: This script must be run from the deployment directory"
        exit 1
    fi

    # Install flutter to a temporary directory if it is not already installed
    if ! command -v flutter &>/dev/null; then
      echo "Installing Flutter"
      VENV=$(mktemp -d)
      git clone https://github.com/flutter/flutter.git -b main ${VENV}/flutter
      export PATH=${VENV}/flutter/bin:${PATH}
      flutter config --no-cli-animations --no-analytics
    fi
    check_command flutter

    # Move to repository root and run tests
    cd ..
    run_unit_tests

    echo "Tests completed successfully."
}

# Validate and parse parameters
parse_params "$@"
# Execute the main function
main "$@"