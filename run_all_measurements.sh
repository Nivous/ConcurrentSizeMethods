#!/bin/bash
#------------------------------------------------------------------------------
# Unified Measurement Runner
# This script runs performance measurements for the concurrent size methods
# by calling Python measurement scripts with appropriate parameters.
#------------------------------------------------------------------------------
set -e

#------------------------------------------------------------------------------
# Configuration Parameters
#------------------------------------------------------------------------------

# Default mode - runs all measurement types
MODE="all"

# Environment settings
multicoreServer=T
JVM_MEM=$([ "$multicoreServer" = "T" ] && echo "31G" || echo "8G")

# Test run parameters
warmupRepeats=5
repeats=10
runtime=5
defaultDSSize=1000000
sizeDelay=700

# Thread and retry configurations based on server type
if [ "$multicoreServer" = "T" ]; then
  workloadThreadsListWithoutSizeThread='[1,4,8,16,32,64]'
  workloadThreadsListWithOneSizeThread='[1,3,7,15,31,63]'
  sizeThreads='[1,4,8,16,32]'
  workloadThreadsWithVariableSizeThreads=32
  workloadThreadsWithOneSizeThread=31
  retriesList='[2,4,8,16]'
else
  workloadThreadsListWithoutSizeThread='[1,3,6]'
  workloadThreadsListWithOneSizeThread='[1,3,6]'
  sizeThreads='[1,3,5]'
  workloadThreadsWithVariableSizeThreads=2
  workloadThreadsWithOneSizeThread=3
  retriesList='[1,2,3]'
fi

#------------------------------------------------------------------------------
# Helper Functions
#------------------------------------------------------------------------------

# Display usage information
usage() {
  echo "Usage: $0 [--mode {regular,zipfian,retries}]" >&2
  echo "  regular  - Run standard overhead experiments" >&2
  echo "  zipfian  - Run Zipfian distribution workload experiments" >&2
  echo "  retries  - Run optimistic retry experiments" >&2
  echo "  all      - Run all experiment types (default)" >&2
  exit 1
}

# Execute a command with timestamp logging
run_cmd() {
  echo -e "\n> $*"
  eval "$*"
  echo "------------------------------------------------------------"
}

#------------------------------------------------------------------------------
# Parse Command-Line Arguments
#------------------------------------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    --mode)
      MODE="$2"
      shift
      ;;
    --mode=*)
      MODE="${1#*=}"
      ;;
    -h|--help)
      usage
      ;;
    *)
      usage
      ;;
  esac
  shift
done

echo -e "\n==============================================================="
echo "STARTING MEASUREMENT SUITE: $MODE MODE"
echo "==============================================================="

#------------------------------------------------------------------------------
# Measurement Execution Functions
#------------------------------------------------------------------------------

# Run overhead experiments with regular distribution
run_java_overhead_experiments() {
  local base="python3 measurements/python_scripts/overhead_runner.py"
  local cmds=(
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithoutSizeThread\" --size-threads 0 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithoutSizeThread\" --size-threads 0 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay $sizeDelay --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay $sizeDelay --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
  )

  for cmd in "${cmds[@]}"; do
    run_cmd "$cmd"
  done

  run_cmd "python3 measurements/python_scripts/scalability_runner.py --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads $workloadThreadsWithVariableSizeThreads --size-threads-list \"$sizeThreads\" --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
  run_cmd "python3 measurements/python_scripts/scalability_runner.py --init-size $defaultDSSize --insert-rate 3 --delete-rate 2 --workload-threads $workloadThreadsWithVariableSizeThreads --size-threads-list \"$sizeThreads\" --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
}

# Run overhead experiments with Zipfian distribution
run_java_zipfian_overhead_experiments() {
  local base="python3 measurements/python_scripts/overhead_runner.py"
  local cmds=(
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithoutSizeThread\" --size-threads 0 --size-delay 0 --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay 0 --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithoutSizeThread\" --size-threads 0 --size-delay 0 --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay 0 --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay $sizeDelay --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --size-threads 1 --size-delay $sizeDelay --zipf --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
  )

  for cmd in "${cmds[@]}"; do
    run_cmd "$cmd"
  done
}

# Run optimistic-retry overhead experiments
run_java_max_retries_experiments() {
  local base1="python3 measurements/python_scripts/overhead_max_retries_runner.py"

  local cmds=(
    "$base1 --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --retries \"$retriesList\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base1 --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --retries \"$retriesList\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base1 --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads \"$workloadThreadsListWithOneSizeThread\" --retries \"$retriesList\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base1 --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads \"$workloadThreadsListWithOneSizeThread\" --retries \"$retriesList\" --size-threads 1 --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
  )
  for cmd in "${cmds[@]}"; do run_cmd "$cmd"; done

  local base2="python3 measurements/python_scripts/scalability_max_retries_runner.py"
  local cmds2=(
    "$base2 --init-size $defaultDSSize --insert-rate 30 --delete-rate 20 --workload-threads $workloadThreadsWithVariableSizeThreads --size-threads-list \"$sizeThreads\" --retries \"$retriesList\" --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
    "$base2 --init-size $defaultDSSize --insert-rate 3  --delete-rate 2  --workload-threads $workloadThreadsWithVariableSizeThreads --size-threads-list \"$sizeThreads\" --retries \"$retriesList\" --size-delay 0 --warmup-runs $warmupRepeats --repeats $repeats --runtime $runtime --jvm-mem $JVM_MEM"
  )

  for cmd in "${cmds2[@]}"; do run_cmd "$cmd"; done
}

#------------------------------------------------------------------------------
# Main Execution Flow
#------------------------------------------------------------------------------

# Always compile before running experiments
run_cmd "./compile.sh"

# Mode dispatch
case "$MODE" in
  regular)
    run_java_overhead_experiments
    ;;

  zipfian)
    run_java_zipfian_overhead_experiments
    ;;

  retries)
    run_java_max_retries_experiments
    ;;

  all|"" )
    run_java_overhead_experiments
    run_java_zipfian_overhead_experiments
    run_java_max_retries_experiments
    ;;

  *)
    usage
    ;;
esac

echo -e "\n==============================================================="
echo "MEASUREMENT SUITE COMPLETED: $MODE MODE"
echo "==============================================================="
