"""Generate graphs from already collected CSV measurement files."""

import os
import re
import logging
import plot_utils as graph
import experiments_environment as env

# Configure logging with color
logging.basicConfig(level=logging.INFO, format='\033[93m%(asctime)s - %(levelname)s - %(message)s\033[0m')

# Number of warmup runs to ignore when averaging results
warmupRepeats = 5 # CHANGE THIS VALUE IF NEEDED

def extract_params(params, param_type):
    """Extract parameters from file name components using regex."""
    patterns = {
        'size': (r"(\d+)setSize", lambda x: int(x.group(1))),
        'ins_rem': (r"(\d+)ins-(\d+)rem", lambda x: map(int, x.groups())),
        'threads': (r"(\d+)(sizeThreads|workloadThreads)", 
                   lambda x: (int(x.group(1)), x.group(2))),
        'delay': (r"(\d+)delay", lambda x: int(x.group(1))),
        'zipf': (r"(\d+(?:\.\d+)?)zipf", lambda x: x.group(1))
    }
    
    pattern, extract_func = patterns[param_type]
    match = re.search(pattern, params)
    if not match:
        raise ValueError(f"{param_type} parameter not found in: {params}")
    return extract_func(match)

def construct_benchmark_name(params):
    """Generate a standardized benchmark name from file parameters."""
    initSize = extract_params(params[1], 'size')
    ins, rmv = extract_params(params[2], 'ins_rem')
    sizeThreads, threads_type = extract_params(params[3], 'threads')
    sizeDelay = extract_params(params[4], 'delay')
    
    return f"{initSize}setSize_{ins}ins-{rmv}rem_{sizeThreads}{threads_type}_{sizeDelay}delay"

def main():
    # Setup output directories
    workingDir = os.path.join(env.DATA_DIR)
    os.makedirs(env.GRAPH_DIR, exist_ok=True)
    for alg in env.baselineDataStructures:
        os.makedirs(os.path.join(env.GRAPH_DIR, alg), exist_ok=True)
    
    # Process all CSV files
    csv_files = [f for f in os.listdir(workingDir) 
                if f.endswith(".csv") and "statistics" not in f]
    logging.info(f"Processing {len(csv_files)} CSV files for graph generation")

    for filename in csv_files:
        params = filename.split("_")
        graph_name = params[0]
        logging.info(f"Generating graphs from {filename}")
        if graph_name == "overhead":
            process_overhead_files(params, graph_name, workingDir)
        elif graph_name == "overhead-zipfian":
            process_zipfian_files(params, graph_name, workingDir)
        elif graph_name == "scalability":
            process_scalability_files(params, graph_name, workingDir)
        elif graph_name in ["overhead-max-retries", "scalability-max-retries"]:
            process_retry_files(params, graph_name, workingDir)

def process_overhead_files(params, graph_name, workingDir):
    """Process standard overhead measurement files."""
    initSize = extract_params(params[1], 'size')
    ins, rmv = extract_params(params[2], 'ins_rem')
    sizeThreads, _ = extract_params(params[3], 'threads')
    sizeDelay = extract_params(params[4], 'delay')
    
    benchmark_name = f"{initSize}setSize_{ins}ins-{rmv}rem_{sizeThreads}sizeThreads_{sizeDelay}delay"
    results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")
    
    united_bars_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_bar_%s_{benchmark_name}.png")
    graph.plot_united_overhead_bars(results_file_path, united_bars_path, warmupRepeats)
    
    united_graph_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_%s_{benchmark_name}.png")
    graph.plot_united_overhead_graph(results_file_path, united_graph_path, warmupRepeats)

def process_zipfian_files(params, graph_name, workingDir):
    """Process zipfian distribution measurement files."""
    initSize = extract_params(params[1], 'size')
    ins, rmv = extract_params(params[2], 'ins_rem')
    sizeThreads, _ = extract_params(params[3], 'threads')
    sizeDelay = extract_params(params[4], 'delay')
    
    benchmark_name = f"{initSize}setSize_{ins}ins-{rmv}rem_{sizeThreads}sizeThreads_{sizeDelay}delay"
    results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")
    
    united_graph_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_%s_{benchmark_name}.png")
    graph.plot_united_overhead_graph(results_file_path, united_graph_path, warmupRepeats)
    
    united_bars_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_bar_%s_{benchmark_name}.png")
    graph.plot_united_overhead_bars(results_file_path, united_bars_path, warmupRepeats)

def process_scalability_files(params, graph_name, workingDir):
    """Process scalability measurement files."""
    initSize = extract_params(params[1], 'size')
    ins, rmv = extract_params(params[2], 'ins_rem')
    workloadThreads, _ = extract_params(params[3], 'threads')
    sizeDelay = extract_params(params[4], 'delay')
    
    benchmark_name = f"{initSize}setSize_{ins}ins-{rmv}rem_{workloadThreads}workloadThreads_{sizeDelay}delay"
    results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")
    
    output_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_sizeThreads_{benchmark_name}.png")
    graph.plot_scalability_graph(results_file_path, output_path, warmupRepeats)

def process_retry_files(params, graph_name, workingDir):
    """Process MAX_TRIES measurement files."""
    benchmark_name = construct_benchmark_name(params)
    results_file_path = os.path.join(env.DATA_DIR, f"{graph_name}_{benchmark_name}.csv")
    
    if graph_name == "overhead-max-retries":
        output_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_%s_{benchmark_name}.png")
        graph.plot_united_retries_overhead_graph(results_file_path, output_path, warmupRepeats)
        
        bars_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_united_%s_{benchmark_name}_bars.png")
        graph.plot_united_retries_overhead_bars(results_file_path, bars_path, warmupRepeats)
        
    elif graph_name == "scalability-max-retries":
        output_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_sizeThreads_{benchmark_name}.png")
        graph.plot_united_retries_scalability_graph(results_file_path, output_path, warmupRepeats)
        
        bars_path = os.path.join(env.GRAPH_DIR, "%s", f"{graph_name}_sizeThreads_{benchmark_name}_bars.png")
        graph.plot_united_retries_scalability_bars(results_file_path, bars_path, warmupRepeats)

if __name__ == "__main__":
    main()