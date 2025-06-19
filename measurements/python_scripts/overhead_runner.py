"""
Run overhead experiments for different numbers of workload threads.
Supports both uniform and Zipfian distributions.
"""

import os
import argparse
from pathlib import Path

import plot_utils as graph
import experiments_environment as env
from experiment_utils import (
    clear_previous_results,
    concat_results,
    java_cmd,
    parse_int_list,
)

parser = argparse.ArgumentParser(
    description="Run overhead experiments for different numbers of workload threads."
)
parser.add_argument("--init-size", type=int, required=True, help="Initial data structure size")
parser.add_argument("--insert-rate", type=int, required=True, help="Percentage of insert operations")
parser.add_argument("--delete-rate", type=int, required=True, help="Percentage of delete operations")
parser.add_argument(
    "--workload-threads",
    type=parse_int_list,
    required=True,
    help="Comma-separated list of workload thread counts",
)
parser.add_argument("--size-threads", type=int, required=True, help="Number of size threads")
parser.add_argument("--size-delay", type=int, required=True, help="Delay between size measurements")
parser.add_argument("--zipf", default=None, action="store_true", help="Optional Zipf distribution parameter")
parser.add_argument("--warmup-runs", type=int, required=True, help="Number of warm-up repetitions")
parser.add_argument("--repeats", type=int, required=True, help="Number of measured repetitions")
parser.add_argument("--runtime", required=True, help="Benchmark runtime per repetition")
parser.add_argument("--jvm-mem", required=True, help="JVM memory size (e.g., 1G)")

args = parser.parse_args()

# Validate inputs
insert_rate = args.insert_rate
delete_rate = args.delete_rate
if insert_rate + delete_rate > 100:
    parser.error("insert-rate + delete-rate must not exceed 100")

# Process arguments
init_size = args.init_size
workload_threads = args.workload_threads
size_threads = str(args.size_threads)
size_delay = str(args.size_delay)
zipf = args.zipf
is_zipfian = zipf is not None
warmup_runs = args.warmup_runs
total_runs = warmup_runs + args.repeats
run_time = args.runtime
jvm_mem = args.jvm_mem

def delete_previous_results() -> None:
    """Remove artifacts from earlier runs."""
    clear_previous_results()

def run_experiments() -> None:
    """Launch the Java benchmarks for all data structures."""
    cmd_base = java_cmd(jvm_mem)
    run_id = 0
    for ds in env.dataStructures:
        for threads in workload_threads:
            run_id += 1
            size_for_ds = size_threads if ds not in env.baselineDataStructures else "0"
            
            # Build command with optional zipfian parameter
            cmd = f"{cmd_base}{threads} {size_for_ds} {total_runs} {run_time} {size_delay} {ds} "
            cmd += f"-ins{insert_rate} -del{delete_rate} "
            
            if is_zipfian:
                cmd += f"-zipf "
                
            cmd += f"-initSize{init_size} -prefill -file-build/data-trials{run_id}.csv"
            
            if os.system(cmd) != 0:
                exit(1)

def create_united_results_file() -> None:
    """Concatenate all trial CSVs into the final results file."""
    concat_results(Path(results_file_path))

def draw_graphs():
    """Generate graphs from the experiment results."""
    os.makedirs(env.GRAPH_DIR, exist_ok=True)
    for alg in env.baselineDataStructures:
        os.makedirs(os.path.join(env.GRAPH_DIR, alg), exist_ok=True)

    # Generate bar charts
    united_bars_file_path = os.path.join(env.GRAPH_DIR, "%s", graph_name + "_united_bar_%s_" + benchmark_name + ".png")
    graph.plot_united_overhead_bars(results_file_path, united_bars_file_path, warmup_runs)

    # Generate line graphs
    united_graph_file_path = os.path.join(env.GRAPH_DIR, "%s", graph_name + "_united_%s_" + benchmark_name + ".png")
    graph.plot_united_overhead_graph(results_file_path, united_graph_file_path, warmup_runs)

# Define output paths based on experiment configuration
graph_name = "overhead-zipfian" if is_zipfian else "overhead"
benchmark_name = f"{init_size}setSize_{insert_rate}ins-{delete_rate}rem_{size_threads}sizeThreads_{size_delay}delay"

results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")

# Execute the experiment pipeline
delete_previous_results()
run_experiments()
create_united_results_file()
draw_graphs()