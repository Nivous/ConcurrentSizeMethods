#!/usr/bin/env bash
# run_on_cores_0_63.sh — confine process & all threads to CPUs 0–63
set -euo pipefail

GRAPHS_DIR=${GRAPHS_DIR:-graphs}
RESULTS_DIR=${RESULTS_DIR:-results}

# Clean outputs
rm -rf "$GRAPHS_DIR" "$RESULTS_DIR"

# Run confined to cores 0–63
numactl --physcpubind=0-63 ./run_all_measurements.sh --mode regular "$@"

