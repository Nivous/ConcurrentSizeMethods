"""Utility functions for generating publication quality figures."""

import logging
from textwrap import wrap
import numpy as np
import matplotlib as mpl
from io_utils import (
    WorkloadOpType,
    getBaselineAlg,
    isSizeAlgorithm,
    read_java_results_file,
    sizeDataStrucutres,
    toString,
    toStringSplit,
)

# Configure logging
logging.basicConfig(level=logging.INFO, format='\033[92m%(asctime)s - %(levelname)s - %(message)s\033[0m')

# Configure matplotlib settings
mpl.use("Agg")

import matplotlib.pyplot as plt

mpl.rcParams["grid.linestyle"] = ":"
mpl.rcParams["grid.color"] = "black"
mpl.rcParams.update({"font.size": 12})

# Constants
areGraphsForPaper = True  # To set hard-coded graph y limits as in the paper

# Algorithm display names
names = {'BST': 'BST',
         'SizeBST': 'SP-BST',
         'OptimisticSizeBST': 'OptimisticBST',
         'HandshakesBST': 'HandshakesBST',
         'StampedLockBST': 'StampedLockBST',
         'BarrierBST': 'BarrierBST',

         'SkipList': 'SkipList',
         'SizeSkipList': 'SP-SkipList',
         'OptimisticSizeSkipList': 'OptimisticSkipList',
         'HandshakesSkipList': 'HandshakesSkipList',
         'StampedLockSkipList': 'StampedLockSkipList',
         'BarrierSkipList': 'BarrierSkipList',

         'HashTable': 'HashTable',
         'SizeHashTable': 'SP-HashTable',
         'OptimisticSizeHashTable': 'OptimisticHashTable',
         'HandshakesHashTable': 'HandshakesHashTable',
         'StampedLockHashTable': 'StampedLockHashTable',
         'BarrierHashTable': 'BarrierHashTable',

         }
colors = {
    'BST': 'C0',
    'SizeBST': 'C2',
    'OptimisticSizeBST': 'C9',
    'HandshakesBST': 'C6',
    'StampedLockBST': 'C3',
    'BarrierBST': 'C4',

    'SkipList': 'C0',
    'SizeSkipList': 'C2',
    'OptimisticSizeSkipList': 'C9',
    'HandshakesSkipList': 'C6',
    'StampedLockSkipList': 'C3',
    'BarrierSkipList': 'C4',

    'HashTable': 'C0',
    'SizeHashTable': 'C2',
    'OptimisticSizeHashTable': 'C9',
    'HandshakesHashTable': 'C6',
    'StampedLockHashTable': 'C3',
    'BarrierHashTable': 'C4',
}

linestyles = {
    'BST': '-',
    'SizeBST': ':',
    'OptimisticSizeBST': ':',
    'HandshakesBST': ':',
    'StampedLockBST': ':',
    'BarrierBST': ':',

    'SkipList': '-',
    'SizeSkipList': ':',
    'OptimisticSizeSkipList': ':',
    'HandshakesSkipList': ':',
    'StampedLockSkipList': ':',
    'BarrierSkipList': ':',

    'HashTable': '-',
    'SizeHashTable': ':',
    'OptimisticSizeHashTable': ':',
    'HandshakesHashTable': ':',
    'StampedLockHashTable': ':',
    'BarrierHashTable': ':',

}

markers = {
    'BST': 'x',
    'SizeBST': 'X',
    'OptimisticSizeBST': '*',
    'HandshakesBST': '2',
    'StampedLockBST': '*',
    'BarrierBST': '1',

    'SkipList': '+',
    'SizeSkipList': 'P',
    'OptimisticSizeSkipList': '*',
    'HandshakesSkipList': '2',
    'StampedLockSkipList': '*',
    'BarrierSkipList': '1',

    'HashTable': '.',
    'SizeHashTable': 'o',
    'OptimisticSizeHashTable': '*',
    'HandshakesHashTable': '2',
    'StampedLockHashTable': '*',
    'BarrierHashTable': '1',
}

# Ordering used for graph legends
algs_order = [
    "HashTable",
    "SizeHashTable",
    "OptimisticSizeHashTable",
    "HandshakesHashTable",
    "StampedLockHashTable",
    "BarrierHashTable",

    "SkipList",
    "SizeSkipList",
    "OptimisticSizeSkipList",
    "HandshakesSkipList",
    "StampedLockSkipList",
    "BarrierSkipList",

    "BST",
    "SizeBST",
    "OptimisticSizeBST",
    "HandshakesBST",
    "StampedLockBST",
    "BarrierBST",
]

def get_datastructure_algs(baselineAlg, algs, include_baseline=False):
    """Get list of algorithms that belong to a data structure family."""
    if include_baseline:
        return [alg for alg in [baselineAlg] + sizeDataStrucutres[baselineAlg] if alg in algs]
    return [alg for alg in sizeDataStrucutres[baselineAlg] if alg in algs]

def plot_united_overhead_bars(input_file_path, output_graph_path, warmupRepeats):
    """Draw a grouped bar chart comparing all algorithms' overhead."""
    
    # Parse input data
    results = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []
    yLimit = 20
    isZipfianMeasurement = 'zipfian' in input_file_path
    
    read_java_results_file(
        input_file_path, results, stddev, workloadThreads, sizeThreads, 
        ratios, initSizes, algs, warmupRepeats, True, False
    )
    
    # Validate input data
    assert (len(sizeThreads) == 1)
    sizeThreadsNum = sizeThreads[0]
    workloadThreads.sort()
    assert (len(initSizes) == 1)
    initSize = initSizes[0]
    assert (len(ratios) == 1)
    percentageRatio = ratios[0]
    
    # Process each baseline algorithm
    for baselineAlg in [baseAlg for baseAlg in algs if baseAlg in sizeDataStrucutres.keys()]:
        # Collect algorithms for this data structure
        datastructureAlgs = [alg for alg in sizeDataStrucutres[baselineAlg] if alg in algs]
        if not datastructureAlgs:
            continue
            
        # Calculate TP loss percentage for each algorithm
        series = {}
        for alg in datastructureAlgs:
            series[alg] = []
            
        for th in workloadThreads:
            baselineKey = toString(baselineAlg, th, 0, initSize, percentageRatio)
            for alg in datastructureAlgs:
                key = toString(alg, th, sizeThreadsNum, initSize, percentageRatio)
                if key in results:
                    # Calculate throughput loss percentage
                    series[alg].append(100 - results[key] / results[baselineKey] * 100)

        # Find min/max values for y-axis scaling
        maxValueDict = {alg: max(max(series[alg]), 10) for alg in datastructureAlgs}
        minValueDict = {alg: min(min(series[alg]), 0) for alg in datastructureAlgs}
        
        maxValue = max(maxValueDict.values())
        ytop = min(maxValue, yLimit) if areGraphsForPaper else maxValue
        ybot = min(minValueDict.values())
        
        # Create plot
        fig, axs = plt.subplots(figsize=(6.5, (ytop - ybot + 2) / 21 * 1.8 - 0.2))
        
        # Set up bar chart parameters
        total_width = 1
        width = (total_width / len(datastructureAlgs)) * 0.75
        x = np.arange(len(workloadThreads))
        to_add = (len(datastructureAlgs) // 2) * (-width)
        
        # Draw bars for each algorithm
        for alg in datastructureAlgs:
            axs.bar(x + to_add, series[alg], width, label=names[alg], color=colors[alg])
            
            # Add value labels on bars
            for i in range(len(workloadThreads)):
                # Format the text and determine positioning
                text_length = 3.55 
                fontsize = 8
                delta = 0.7
                
                rounded_value = round(series[alg][i], 1)
                if rounded_value == 0.0:
                    rounded_value = 0
                    text_length -= 0.3
                    
                formatted_value = f"{rounded_value:.1f}"
                if formatted_value.endswith('.0'):
                    formatted_value = formatted_value[:-2]
                    text_length -= 1.5

                # Position text based on value
                if rounded_value >= text_length:
                    text_color = 'white'  # White text for dark backgrounds
                    axs.text((x + to_add)[i], min(rounded_value, 20) - delta,
                             formatted_value, ha='center', va='bottom',
                             fontsize=fontsize, rotation=90, color=text_color)
                elif rounded_value < text_length and rounded_value >= 0:
                    text_color = 'black'  # Black text for light backgrounds
                    axs.text((x + to_add)[i], min(rounded_value + text_length, 20),
                             formatted_value, ha='center', va='bottom',
                             fontsize=fontsize, rotation=90, color=text_color)
                else:
                    text_color = 'red'  # Red text for negative values
                    axs.text((x + to_add)[i], text_length + 0.7,
                             formatted_value, ha='center', va='bottom',
                             fontsize=fontsize, rotation=90, color=text_color)
            
            to_add += width
            
        # Configure plot appearance
        axs.set_xticks(x)
        axs.set_xticklabels(workloadThreads)
        plt.tick_params(axis='x', bottom=False, top=True, labelbottom=False, labeltop=True)
        
        # Set y-axis ticks and labels
        TP_loss_percentages = np.arange(20) * 10 - 100
        axs.set_yticks(TP_loss_percentages)
        axs.set_yticklabels(TP_loss_percentages)
        axs.set(ylabel='% TP loss')
        axs.set_ylim(bottom=ybot, top=ytop + 2)
        axs.invert_yaxis()
        
        # Remove spines
        for spine in ['bottom', 'right', 'left', 'top']:
            axs.spines[spine].set_visible(False)
            
        axs.set_axisbelow(True)
        
        # Add horizontal grid lines
        yLineValue = -100
        while yLineValue <= ytop + 2:
            linestyle = '-' if yLineValue == 0 else '--'
            plt.axhline(y=yLineValue, linewidth=0.8, color='k', linestyle=linestyle, alpha=0.4)
            yLineValue += 10
            
        # Save the figure
        path = output_graph_path % (baselineAlg, baselineAlg)
        plt.savefig(path, bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_united_overhead_graph(input_file_path, output_graph_path, warmupRepeats):
    """Plot all algorithms on one overhead graph."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []
    isZipfianMeasurement = 'zipfian' in input_file_path

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, True)
    assert (len(sizeThreads) == 1)
    sizeThreadsNum = sizeThreads[0]
    workloadThreads.sort()
    assert (len(initSizes) == 1)
    initSize = initSizes[0]
    assert (len(ratios) == 1)
    percentageRatio = ratios[0]

    for baselineAlg in sizeDataStrucutres.keys():
        sizeAlgs = sizeDataStrucutres[baselineAlg]
        _datastructureAlgs = [baselineAlg] + sizeAlgs
        datastructureAlgs = [alg for alg in _datastructureAlgs if alg in algs]
        if len(datastructureAlgs) == 0:
            continue
        series = {}
        error = {}
        ymax = 0

        for alg in datastructureAlgs:
            series[alg] = []
            error[alg] = []

        for th in workloadThreads:
            for alg in datastructureAlgs:
                if isSizeAlgorithm(alg):
                    key = toString(alg, th, sizeThreadsNum,
                                   initSize, percentageRatio)
                else:
                    key = toString(baselineAlg, th, 0,
                                   initSize, percentageRatio)
                assert key in throughput
                series[alg].append(throughput[key])
                error[alg].append(stddev[key])

        if areGraphsForPaper:
            fig, axs = plt.subplots(figsize=(6.5, 2.2))
        else:
            fig, axs = plt.subplots(figsize=(6.5, 4.2))
        opacity = 0.8
        rects = {}

        for alg in datastructureAlgs:
            ymax = max(ymax, max(series[alg]))
            rects[alg] = axs.plot(workloadThreads, series[alg],
                                  alpha=opacity,
                                  color=colors[alg],
                                  linestyle=linestyles[alg],
                                  linewidth=1.75,
                                  marker=markers[alg],
                                  markersize=7,
                                  label=names[alg])

        if areGraphsForPaper:
            if 'HashTable' in alg:
                if isZipfianMeasurement:
                    ytop = 200
                else:
                    ytop = 175
            elif 'BST' in alg:
                ytop = 60
            else:
                ytop = 40
            axs.set_ylim(bottom=-0.02 * ytop, top=ytop)
        else:
            axs.set_ylim(bottom=-0.02 * ymax)
        plt.xticks(workloadThreads, workloadThreads)
        ylabel = 'Workload threads total TP (Mop/s)'
        wrapped_ylabel = "\n".join(wrap(ylabel, 20))
        axs.set(xlabel='Workload threads', ylabel=wrapped_ylabel)
        legend_x = 1
        legend_y = 0.5
        legend = plt.legend(loc='center left', bbox_to_anchor=(
            legend_x, legend_y), ncol=len(rects), fontsize='xx-small')

        legend_name = "legend_zipfian_overhead" if isZipfianMeasurement else "legend_overhead"
        export_legend(
            legend, f"graphs/{baselineAlg}/{legend_name}.png")
        legend.remove()

        plt.grid()
        axs.set_axisbelow(True)
        plt.savefig(output_graph_path %
                    (baselineAlg, baselineAlg), bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_scalability_graph(input_file_path, output_graph_path, warmupRepeats):
    """Plot scalability curves for size threads."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, False)
    assert (len(workloadThreads) == 1)
    workloadThreadsNum = workloadThreads[0]
    sizeThreads.sort()

    ymax = 0
    series = {}
    error = {}
    for baselineAlg in list(set([getBaselineAlg(alg) for alg in algs])):
        if baselineAlg == None:
            continue
        series = {}
        rects = {}
        datastructureAlgs = [
            alg for alg in sizeDataStrucutres[baselineAlg] if alg in algs]
        for alg in datastructureAlgs:
            series[alg] = []
            error[alg] = []
            for th in sizeThreads:
                if toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0]) not in throughput:
                    del series[alg]
                    del error[alg]
                    break
                series[alg].append(
                    throughput[toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0])])
                error[alg].append(
                    stddev[toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0])])

        if areGraphsForPaper:
            fig, axs = plt.subplots(figsize=(6.5, 2.2))
        else:
            fig, axs = plt.subplots(figsize=(6.5, 4.2))
        opacity = 0.8
        rects = {}

        for alg in algs_order:
            if not alg in series:
                continue
            ymax = max(ymax, max(series[alg]))
            rects[alg] = axs.plot(sizeThreads, series[alg],
                                  alpha=opacity,
                                  color=colors[alg],
                                  linestyle=linestyles[alg],
                                  linewidth=1.75,
                                  marker=markers[alg],
                                  markersize=7,
                                  label=names[alg])

        if areGraphsForPaper:
            if 'HashTable' in baselineAlg:
                if '30ins' in input_file_path:
                    ytop = 4000
                else:
                    ytop = 2000
            elif 'BST' in baselineAlg:
                if '30ins' in input_file_path:
                    ytop = 1500
                else:
                    ytop = 5250
            elif 'SkipList' in baselineAlg:
                if '30ins' in input_file_path:
                    ytop = 1500
                else:
                    ytop = 5900
            axs.set_ylim(bottom=-0.02 * ytop, top=ytop)
        else:
            axs.set_ylim(bottom=-0.02 * ymax)

        plt.xticks(sizeThreads, sizeThreads)
        ylabel = 'Size threads total TP (Kop/s)'
        wrapped_ylabel = "\n".join(wrap(ylabel, 17))

        axs.set(xlabel='Size threads', ylabel=wrapped_ylabel)

        legend_x = 1
        legend_y = 0.5
        legend = plt.legend(loc='center left', bbox_to_anchor=(
            legend_x, legend_y), ncol=len(rects), fontsize='xx-small')
        export_legend(legend, f"graphs/{baselineAlg}/legend_scalability.png")
        legend.remove()

        plt.grid()
        axs.set_axisbelow(True)
        plt.savefig(output_graph_path %
                    baselineAlg, bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_united_retries_overhead_bars(input_file_path, output_graph_path, warmupRepeats):
    """Draw grouped bars for overhead retry experiments."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, True)
    assert (len(sizeThreads) == 1)
    sizeThreadsNum = sizeThreads[0]
    workloadThreads.sort()
    assert (len(initSizes) == 1)
    initSize = initSizes[0]
    assert (len(ratios) == 1)
    percentageRatio = ratios[0]



    for baselineAlg in sizeDataStrucutres.keys():
        if baselineAlg == 'BST':
            max_y = 60
            jump = 10
        elif baselineAlg == "HashTable":
            max_y = 200
            jump = 50
        else:
            max_y = 40
            jump = 10
        datastructureAlgs = sorted(
            [alg for alg in algs if baselineAlg in alg and "32" not in alg and "64" not in alg], key=lambda x: (len(x), x))
        if datastructureAlgs == []:
            continue
        datastructureAlgs.remove(baselineAlg)
        datastructureAlgs.insert(0, baselineAlg)
        series = {}
        rects = {}
        for alg in datastructureAlgs:
            series[alg] = []
        for th in workloadThreads:
            for alg in datastructureAlgs:
                if isSizeAlgorithm(alg):
                    key = toString(alg, th, sizeThreadsNum,
                                   initSize, percentageRatio)
                else:
                    key = toString(baselineAlg, th, 0,
                                   initSize, percentageRatio)
                assert key in throughput
                series[alg].append(throughput[key])

        fig, axs = plt.subplots(figsize=(6.5, 4.2))
        total_width = 1
        width = (total_width/len(datastructureAlgs))*0.75
        x = np.arange(len(workloadThreads))
        to_add = (len(datastructureAlgs)//2)*(-width)

        _colors = ['C0', 'C5', 'C6', 'C7', 'C8', 'C9', 'C10', 'C11']
        ci = 0

        for alg in datastructureAlgs:
            rects[alg] = axs.bar(x+to_add, series[alg],
                                 width, label=alg, color=_colors[ci])
            for i in range(len(workloadThreads)):
                rounded_value = f"{round(series[alg][i], 1):.1f}"
                if rounded_value.endswith('.0'):
                    rounded_value = rounded_value[:-2]
                textSpace = max_y/50
                axs.text((x+to_add)[i], series[alg][i] + textSpace, rounded_value,
                         ha='center', va='bottom', rotation=90, fontsize=7)
            to_add += width
            ci += 1
        axs.set_xticks(x)
        axs.set_xticklabels(workloadThreads)
        plt.tick_params(axis='x', bottom=True, top=False,
                        labelbottom=True, labeltop=False)
        ylabel = 'Workload threads total TP (Mop/s)'
        xlabel = 'Workload threads'
        axs.set(xlabel=xlabel)
        axs.set(ylabel=ylabel)
        axs.set_ylim(top=max_y)
        axs.set_ylim(bottom=0)
        axs.spines['right'].set_visible(False)
        axs.spines['left'].set_visible(False)
        axs.spines['top'].set_visible(False)
        axs.set_axisbelow(True)

        yLineValue = jump
        while yLineValue <= max_y:
            plt.axhline(y=yLineValue, linewidth=0.5, alpha=0.5,
                        color='k', linestyle='--' if yLineValue != 0 else '-')

            yLineValue += jump
        path = output_graph_path % (baselineAlg, baselineAlg)
        plt.savefig(path, bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_united_retries_overhead_graph(input_file_path, output_graph_path, warmupRepeats):
    """Draw overhead graphs for retry experiments."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, True)
    assert (len(sizeThreads) == 1)
    sizeThreadsNum = sizeThreads[0]
    workloadThreads.sort()
    assert (len(initSizes) == 1)
    initSize = initSizes[0]
    assert (len(ratios) == 1)
    percentageRatio = ratios[0]
    for baselineAlg in sizeDataStrucutres.keys():
        datastructureAlgs = sorted(
            [alg for alg in algs if baselineAlg in alg], key=lambda x: (len(x), x))
        if len(datastructureAlgs) == 0:
            continue
        datastructureAlgs.remove(baselineAlg)
        datastructureAlgs.insert(0, baselineAlg)
        series = {}
        error = {}
        ymax = 0
        for alg in datastructureAlgs:
            series[alg] = []
            error[alg] = []

        for th in workloadThreads:
            for alg in datastructureAlgs:
                if isSizeAlgorithm(alg):
                    key = toString(alg, th, sizeThreadsNum,
                                   initSize, percentageRatio)
                else:
                    key = toString(baselineAlg, th, 0,
                                   initSize, percentageRatio)
                assert key in throughput
                series[alg].append(throughput[key])
                error[alg].append(stddev[key])

        fig, axs = plt.subplots(figsize=(6.5, 4.2))
        opacity = 0.8
        rects = {}
        i = 5
        for alg in datastructureAlgs:
            if alg == baselineAlg:
                ymax = max(ymax, max(series[alg]))
                rects[alg] = axs.plot(workloadThreads, series[alg],
                                      alpha=opacity,
                                      color=colors[alg],
                                      linestyle='dotted',
                                      linewidth=1.75,
                                      marker='x',
                                      markersize=7,
                                      label=alg)
                continue
            ymax = max(ymax, max(series[alg]))

            # Shorten algorithm names for this legend
            new_alg_name = alg.replace("Optimistic", "Opt")
            new_alg_name = new_alg_name.replace("Size", "")

            rects[alg] = axs.plot(workloadThreads, series[alg],
                                  alpha=opacity,
                                  color='C'+str(i),
                                  linestyle='dotted',
                                  linewidth=1.75,
                                  marker='x',
                                  markersize=7,
                                  label=new_alg_name)
            i += 1

        if areGraphsForPaper:
            if 'HashTable' in alg:
                ytop = 200
            elif 'BST' in alg:
                ytop = 60
            else:
                ytop = 68
            axs.set_ylim(bottom=-0.02 * ytop, top=ytop)
        else:
            axs.set_ylim(bottom=-0.02 * ymax)

        plt.xticks(workloadThreads, workloadThreads)
        axs.set(xlabel='Workload threads',
                ylabel='Workload threads total TP (Mop/s)')
        legend_x = 1
        legend_y = 0.5
        legend = plt.legend(loc='center left', bbox_to_anchor=(
            legend_x, legend_y), ncol=len(rects), fontsize='xx-small')
        export_legend(
            legend, f"graphs/{baselineAlg}/legend_retries_overhead.png")
        legend.remove()

        plt.grid()
        axs.set_axisbelow(True)
        plt.savefig(output_graph_path %
                    (baselineAlg, baselineAlg), bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_united_retries_scalability_graph(input_file_path, output_graph_path, warmupRepeats):
    """Plot retry scalability curves for all algorithms."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, False)
    assert (len(workloadThreads) == 1)
    workloadThreadsNum = workloadThreads[0]
    sizeThreads.sort()

    ymax = 0
    series = {}
    error = {}
    for baselineAlg in sizeDataStrucutres.keys():
        datastructureAlgs = sorted(
            [alg for alg in algs if baselineAlg in alg], key=lambda x: (len(x), x))
        if datastructureAlgs == []:
            continue
        series = {}
        rects = {}
        for alg in datastructureAlgs:
            series[alg] = []
            error[alg] = []
            for th in sizeThreads:
                if toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0]) not in throughput:
                    del series[alg]
                    del error[alg]
                    break
                series[alg].append(
                    throughput[toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0])])
                error[alg].append(
                    stddev[toString(alg, workloadThreadsNum, th, initSizes[0], ratios[0])])
        fig, axs = plt.subplots(figsize=(6.5, 4.2))
        opacity = 0.8
        rects = {}

        i = 6
        for alg in datastructureAlgs:
            
            # Shorten algorithm names for this legend
            new_alg_name = alg.replace("Optimistic", "Opt")
            new_alg_name = new_alg_name.replace("Size", "")

            ymax = max(ymax, max(series[alg]))
            rects[alg] = axs.plot(sizeThreads, series[alg],
                                  alpha=opacity,
                                  color='C'+str(i),
                                  linestyle='dotted',
                                  linewidth=1.75,
                                  marker='x',
                                  markersize=7,
                                  label=new_alg_name)
            i += 1

        if areGraphsForPaper:
            ytop = 3500
            if 'BST' in baselineAlg:
                ytop = 5250
            axs.set_ylim(bottom=-0.02 * ytop, top=ytop)
        else:
            axs.set_ylim(bottom=-0.02 * ymax)

        plt.xticks(sizeThreads, sizeThreads)
        ylabel = 'Size threads total TP (Kop/s)'
        wrapped_ylabel = "\n".join(wrap(ylabel, 20))

        axs.set(xlabel='Size threads', ylabel=wrapped_ylabel)

        legend_x = 1
        legend_y = 0.5
        legend = plt.legend(loc='center left', bbox_to_anchor=(
            legend_x, legend_y), ncol=len(rects), fontsize='xx-small')
        export_legend(
            legend, f"graphs/{baselineAlg}/legend_retries_scalability.png")
        legend.remove()

        plt.grid()
        axs.set_axisbelow(True)
        plt.savefig(output_graph_path %
                    baselineAlg, bbox_inches='tight', dpi=300)
        plt.close('all')

def plot_united_retries_scalability_bars(input_file_path, output_graph_path, warmupRepeats):
    """Draw grouped bars for retry scalability experiments."""
    throughput = {}
    stddev = {}
    workloadThreads = []
    sizeThreads = []
    ratios = []
    initSizes = []
    algs = []

    read_java_results_file(input_file_path, throughput, stddev, workloadThreads, sizeThreads, ratios, initSizes, algs,
                           warmupRepeats, False)
    assert (len(workloadThreads) == 1)
    workloadThreadsNum = workloadThreads[0]
    sizeThreads.sort()
    assert (len(initSizes) == 1)
    initSize = initSizes[0]
    assert (len(ratios) == 1)
    percentageRatio = ratios[0]
    for baselineAlg in sizeDataStrucutres.keys():
        if baselineAlg == 'BST':
            max_y = 3000
            jump = 500
        elif baselineAlg == "HashTable":
            max_y = 3000
            jump = 500
        else:
            max_y = 3000
            jump = 500

        datastructureAlgs = sorted(
            [alg for alg in algs if baselineAlg in alg and "32" not in alg and "64" not in alg], key=lambda x: (len(x), x))
        if datastructureAlgs == []:
            continue
        series = {}
        rects = {}
        for alg in datastructureAlgs:
            series[alg] = []
        for th in sizeThreads:
            for alg in datastructureAlgs:
                key = toString(alg, workloadThreadsNum, th,
                               initSize, percentageRatio)
                assert key in throughput
                series[alg].append(throughput[key])

        fig, axs = plt.subplots(figsize=(6.5, 4.2))
        total_width = 1
        width = (total_width/len(datastructureAlgs))*0.75
        x = np.arange(len(sizeThreads))
        to_add = (len(datastructureAlgs)//2)*(-width)

        _colors = ['C5', 'C6', 'C7', 'C8', 'C9', 'C10', 'C11']
        ci = 0

        for alg in datastructureAlgs:
            rects[alg] = axs.bar(x+to_add, series[alg],
                                 width, label=alg, color=_colors[ci])
            for i in range(len(sizeThreads)):
                roundedValue = round(series[alg][i])
                textSpace = max_y/50
                axs.text((x+to_add)[i], series[alg][i] + textSpace, roundedValue,
                         ha='center', va='bottom', rotation=90, fontsize=7)
            to_add += width
            ci += 1
        axs.set_xticks(x)
        axs.set_xticklabels(sizeThreads)
        plt.tick_params(axis='x', bottom=True, top=False,
                        labelbottom=True, labeltop=False)
        ylabel = 'Size threads total TP (Kop/s)'
        xlabel = 'Size threads'
        axs.set(xlabel=xlabel)
        axs.set(ylabel=ylabel)
        axs.set_ylim(top=max_y)
        axs.set_ylim(bottom=0)
        axs.spines['right'].set_visible(False)
        axs.spines['left'].set_visible(False)
        axs.spines['top'].set_visible(False)
        axs.set_axisbelow(True)
        yLineValue = jump
        while yLineValue <= max_y:
            plt.axhline(y=yLineValue, linewidth=0.5, alpha=0.5,
                        color='k', linestyle='--' if yLineValue != 0 else '-')
            yLineValue += jump
        path = output_graph_path % (baselineAlg)
        plt.savefig(path, bbox_inches='tight', dpi=300)
        plt.close('all')

def export_legend(legend, filename):
    """Save legend to a standalone image file."""
    fig = legend.figure
    fig.canvas.draw()
    bbox = legend.get_window_extent().transformed(fig.dpi_scale_trans.inverted())
    fig.savefig(filename, dpi=300, bbox_inches=bbox)