import os
import argparse
from pathlib import Path

import plot_utils as graph
import experiments_environment as env
from experiment_utils import clear_previous_results, concat_results, java_cmd, parse_int_list

parser = argparse.ArgumentParser(
    description="Run scalability experiments and optionally draw graphs."
)
parser.add_argument("--init-size", type=int, required=True, help="Initial data structure size")
parser.add_argument("--insert-rate", type=int, required=True, help="Percentage of insert operations")
parser.add_argument("--delete-rate", type=int, required=True, help="Percentage of delete operations")
parser.add_argument("--workload-threads", type=int, required=True, help="Number of workload threads")
parser.add_argument("--size-threads-list", type=parse_int_list, required=True, help="Comma-separated list of size thread counts")
parser.add_argument("--size-delay", type=int, required=True, help="Delay between size measurements")
parser.add_argument("--warmup-runs", type=int, required=True, help="Number of warm-up repetitions")
parser.add_argument("--repeats", type=int, required=True, help="Number of measured repetitions")
parser.add_argument("--runtime", required=True, help="Benchmark runtime per repetition")
parser.add_argument("--jvm-mem", required=True, help="JVM memory size (e.g., 1G)")
# Experiments always run before graphs are drawn

args = parser.parse_args()

insert_rate = args.insert_rate
delete_rate = args.delete_rate
if insert_rate + delete_rate > 100:
    parser.error("insert-rate + delete-rate must not exceed 100")

init_size = args.init_size
workload_threads = str(args.workload_threads)
size_threads_list = args.size_threads_list
size_delay = str(args.size_delay)
warmup_runs = args.warmup_runs
total_runs = warmup_runs + args.repeats
run_time = args.runtime
jvm_mem = args.jvm_mem

def delete_previous_results() -> None:
    clear_previous_results()

def run_experiments() -> None:
    cmd_base = java_cmd(jvm_mem)

    i = 0
    for ds in env.dataStructures:
        if ds in env.baselineDataStructures:
            continue
        for size_threads in size_threads_list:
            i += 1
            cmd = (
                f"{cmd_base}{workload_threads} {size_threads} {total_runs} {run_time} {size_delay} "
                f"{ds} -ins{insert_rate} -del{delete_rate} -initSize{init_size} -prefill -file-build/data-trials{i}.csv"
            )
            if os.system(cmd) != 0:
                exit(1)

def create_united_results_file() -> None:
    concat_results(Path(results_file_path))

def draw_graphs():
    os.makedirs(env.GRAPH_DIR, exist_ok=True)
    for alg in env.baselineDataStructures:
        os.makedirs(os.path.join(env.GRAPH_DIR,alg), exist_ok=True)
    graph.plot_scalability_graph(results_file_path,
                                 os.path.join(env.GRAPH_DIR, "%s" , graph_name + "_sizeThreads_" + benchmark_name + ".png"),
                                 warmup_runs)

graph_name = "scalability"
benchmark_name = (
    f"{init_size}setSize_{insert_rate}ins-{delete_rate}rem_{workload_threads}workloadThreads_{size_delay}delay"
)
results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")

delete_previous_results()
run_experiments()
create_united_results_file()
draw_graphs()