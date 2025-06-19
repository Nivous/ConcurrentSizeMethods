# Artifact for "A Study of Synchronization Methods for Concurrent Size"

This repository contains the artifact for the paper "A Study of Synchronization Methods for Concurrent Size" by Hen Kas-Sharir, Gal Sela, and Erez Petrank, SPAA 2025 (https://doi.org/10.1145/3694906.3743316).

## Overview
This artifact contains implementations of several synchronization methodologies for computing the size operation in standard concurrent data structures, accompanied by a complete benchmarking framework. It enables full reproduction of all experimental results presented in the paper and facilitates further investigation of the trade-offs among the various approaches to concurrent size computation.

The following methodologies are included:

- Handshake-based methodology
- Lock-based methodology
- Optimistic methodology
- Wait-free size methodology (based on the "Concurrent Size" paper by Sela and Petrank)

## Artifact Structure

```
README.md              - This documentation file
run_all_measurements.sh - Script to compile and run all measurements
compile.sh             - Compiles the Java sources
run_tests.sh           - Test execution script
LICENSE                - GNU General Public License v3.0
graphs_gen.tex         - LaTeX file to generate the graphs in a pdf file 
                         after producing all graphs
.gitignore             - Specifies intentionally untracked files to ignore
algorithms/            - Concurrent data structure implementations
├── baseline/          - Original data structures without size support:
│                        skip list, hash table and BST
│
├── size/              - Data structures transformed with our methodologies
│   ├── core/          - Core infrastructure used by all methodologies
│   ├── handshakes/    - Handshake-based methodology implementation
│   ├── locks/         - Lock-based methodology implementation
│   ├── optimistic/    - Optimistic methodology implementation 
│   └── sp/            - Wait-free size methodology implementation
│                        (based on "Concurrent Size" paper)
│
├── vcas/              - BST with snapshot mechanism 
│                        (based on "Constant-Time Snapshots with
│                        Applications to Concurrent Data Structures")
│
└── iterator/          - Skip list with snapshot mechanism
│                        (based on "Lock-Free Data-Structure Iterators")
measurements/          - Benchmarking and analysis code
├── Main.java          - Main driver for measurements and tests
├── support/           - Auxiliary Java code including workload generators
├── adapters/          - Adapters for unified measurement interface
└── python_scripts/    - Analysis scripts and graph generation tools
```

# Getting Started

## Prerequisites

### Java Development Kit
- OpenJDK 21 or later recommended
- Installation on Linux: `sudo apt-get install openjdk-21-jdk openjdk-21-jre -y`

### Python Environment
- Python 3.8+
- Required packages: `pip3 install matplotlib numpy`

## Experiment Workflow

### 1. Configure Parameters

The `run_all_measurements.sh` script contains configurable parameters for different hardware environments:

#### Hardware Configuration

```bash
# At the top of run_all_measurements.sh:
multicoreServer=T  # Set to 'T' for servers, 'F' for desktop/laptop
```

This setting automatically adjusts:

| Parameter | Server Mode (T) | Personal Computer Mode (F) |
|-----------|----------------|---------------------------|
| Workload threads | 1-64 | 1-6 |
| Size threads | 1-32 | 1-5 |
| JVM memory | 31GB | 8GB |
| Retry counts | 2,4,8,16 | 1,2,3 |

#### CPU Node Affinity for Consistent Measurements

If your benchmark machine has multiple CPU nodes, consider using `taskset` to bind the experiment to a single CPU node. This ensures that all threads run on the same node, reducing variability caused by inter-node communication.

For example, to bind the experiment to CPUs 0-15 (assuming these belong to a single node):

```bash
taskset --cpu-list 0-15 ./run_all_measurements.sh
```

Adjust the CPU range based on your machine's topology and the size of a single node.

#### False Sharing Prevention

To prevent false sharing (when multiple threads access different variables on the same cache line), the code uses:

- `Padding.PADDING = 8` in `measurements/support/Padding.java` - adds 64 bytes (8 longs × 8 bytes) between fields
- `-XX:ContendedPaddingWidth=64` JVM flag - controls padding for `@Contended` annotations

For CPUs with larger cache lines (typically 64-128 bytes), consider increasing these values if you observe performance degradation under high thread counts.

#### Measurement Parameters

```bash
# Performance measurement settings
warmupRepeats=5      # Warmup iterations
repeats=10           # Measured iterations
runtime=5            # Seconds per iteration
defaultDSSize=1000000 # Initial data structure size
sizeDelay=700        # Microsecond delay per size operation
```

#### Quick Testing Configuration

For faster preliminary testing:

```bash
# Quick test settings
multicoreServer=F
warmupRepeats=1
repeats=2
runtime=1
```

### 2. Run Correctness Tests (Optional)

```bash
./compile.sh
./run_tests.sh
```
This verifies the implementation correctness of all data structures.

### 3. Run Performance Measurements

Before running measurements, back up or remove any existing results directories.

The benchmark script supports four execution modes with different measurement types:

```bash
# Available measurement modes:
./run_all_measurements.sh --mode regular   # Runs two measurement types:
                                           # 1. Overhead with uniform key distribution
                                           # 2. Size thread scalability measurements

./run_all_measurements.sh --mode zipfian   # Runs overhead measurements with skewed
                                           # (Zipfian) key distribution
                                           # in contains operations

./run_all_measurements.sh --mode retries   # Runs optimistic methodology experiments
                                           # with varying MAX_TRIES parameters

./run_all_measurements.sh                  # Runs all three modes above
```

The experiments will:
1. Compile all Java code
2. Execute the specified measurements
3. Generate CSV results in `measurements/results/`
4. Create graphs in `measurements/graphs/`

Execution progress is displayed in the terminal, with each benchmark run showing dots for completed iterations.

### 4. Generate PDF Report

After running experiments, compile the graphs into a comprehensive report:

```bash
pdflatex graphs_gen.tex
```

## Advanced Usage

### Running Specific Data Structure Combinations

To focus on specific data structures, modify the configuration in `measurements/python_scripts/experiments_environment.py`:

```python
# Example: Test only BST with Optimistic methodology
dataStructures = ["BST", "OptimisticSizeBST"]
```

### Generating Graphs Without Re-running Experiments

If you've already collected measurement data (CSV files) in the measurements/results/ directory from previous runs, you can regenerate graphs without re-running the entire measurement suite:

```bash
python3 measurements/python_scripts/plot_graphs_from_existing_results.py
```

By default, the script uses 5 warmup runs (same as the main measurement scripts). If you used a different number of warmup runs, you can modify the `warmupRepeats` variable at the top of the script.

## Understanding the Results

Our experiments measure four key aspects of the provided data structures.

### 1. Uniform Overhead (Figures 6-8)
These experiments show how our size methodologies affect the base data structure performance. While absolute throughput will vary by machine, our methodologies should maintain reasonable overhead compared to the baseline.

### 2. Zipfian Overhead (Figures 15-17)
These evaluate performance under skewed access patterns, simulating realistic hotspot workloads. The relative performance of the different methodologies provides insights into contention handling capabilities.

### 3. Size Scalability (Figures 9-11)
These measure how size operation throughput scales with increasing size threads. Our methodologies should demonstrate better scalability compared to snapshot-based approaches.

### 4. MAX_TRIES Measurements (Figures 12-14)
These analyze how the MAX_TRIES parameter affects the optimistic methodology, illustrating the trade-off between the performance of the size operation and that of non-size operations.
