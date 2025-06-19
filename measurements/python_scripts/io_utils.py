"""Utility helpers for parsing CSV benchmark results."""

import csv
import os
import logging
import statistics as st
from typing import Dict, List
import enum

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - \033[96m%(levelname)s\033[0m - %(message)s')

class WorkloadOpType(enum.Enum):
    """Operation types recorded in the CSV results."""
    all = -1.5
    insert = -0.5
    delete = 0.5
    contains = 1.5

# CSV column order produced by the Java benchmarks
columns = [
    "name", "nWorkloadThreads", "nSizeThreads", "percentageRatio", "initSize", 
    "time", "workloadThreadsThroughput", "sizeThreadsThroughput", 
    "ninstrue", "ninsfalse", "ndeltrue", "ndelfalse", "ncontainstrue", "ncontainsfalse", 
    "totalelapsedinstime", "totalelapseddeltime", "totalelapsedcontainstime",
]

# Mapping from baseline data structure to its size-aware variants
sizeDataStrucutres = {
    "BST": ["SizeBST", "OptimisticSizeBST", "HandshakesBST", "StampedLockBST"],
    "SkipList": ["SizeSkipList", "OptimisticSizeSkipList", "HandshakesSkipList", "StampedLockSkipList"],
    "HashTable": ["SizeHashTable", "OptimisticSizeHashTable", "HandshakesHashTable", "StampedLockHashTable"],
}

def flatten(l: List[List[str]]) -> List[str]:
    """Return a flattened copy of a list of lists."""
    return [item for sublist in l for item in sublist]

def isSizeAlgorithm(alg: str) -> bool:
    """Return True if ``alg`` is a size-aware data structure."""
    alg = alg.split("-")[0]
    return alg in flatten(sizeDataStrucutres.values())

def getBaselineAlg(sizeAlg: str):
    """Return the baseline algorithm for ``sizeAlg`` if any."""
    if not isSizeAlgorithm(sizeAlg):
        return None
    for key, value in sizeDataStrucutres.items():
        if sizeAlg in value:
            return key
    return None

def toString(algname: str, workloadThreadsNum: int, sizeThreadsNum: int, 
            initSize: int, percentageRatio: str) -> str:
    """Return a key string for lookup dictionaries."""
    return f"{algname}-{workloadThreadsNum}w-{sizeThreadsNum}s-{initSize}k-{percentageRatio}"

def toStringSplit(algname: str, workloadThreadsNum: int, sizeThreadsNum: int, 
                 initSize: int, percentageRatio: str, workloadOpType: WorkloadOpType) -> str:
    """Return a key including the workload operation type."""
    return toString(algname, workloadThreadsNum, sizeThreadsNum, initSize, percentageRatio) + f"r-{workloadOpType.name}"

def avg(numlist: List[float]) -> float:
    """Return the arithmetic mean of ``numlist`` or ``-1`` if empty."""
    return sum(float(num) for num in numlist) / len(numlist) if numlist else -1

def read_java_results_file(path: str, results: Dict[str, float], stddev: Dict[str, float], 
                         workloadThreads: List[int], sizeThreads: List[int], ratios: List[str], 
                         initSizes: List[int], algs: List[str], warmupRepeats: int, 
                         isWorkloadThreadsTP: bool, isSplitByOpType: bool = False) -> None:
    """Parse ``path`` and populate the provided result containers."""
    columnIndex = {}
    resultsRaw = {}

    # read csv into resultsRaw
    try:
        with open(path, newline='') as csvfile:
            csvreader = csv.reader(csvfile, delimiter=',', quotechar='|')
            for row in csvreader:
                if not bool(columnIndex):  # columnIndex dictionary is empty
                    for col in columns:
                        columnIndex[col] = row.index(col)
                if row[columnIndex[columns[0]]] == columns[0]:  # row contains column titles
                    continue
                values = {}
                for col in columns:
                    values[col] = row[columnIndex[col]]
                if int(values['nWorkloadThreads']) not in workloadThreads:
                    workloadThreads.append(int(values['nWorkloadThreads']))
                if isSizeAlgorithm(values['name']) and int(values['nSizeThreads']) not in sizeThreads:
                    sizeThreads.append(int(values['nSizeThreads']))
                if int(values['initSize']) not in initSizes:
                    initSizes.append(int(values['initSize']))
                if values['percentageRatio'] not in ratios:
                    ratios.append(values['percentageRatio'])
                if values['name'] not in algs:
                    algs.append(values['name'])
                time = float(values['time'])

                if not isSplitByOpType:
                    key = toString(values['name'], values['nWorkloadThreads'], values['nSizeThreads'], values['initSize'],
                                values['percentageRatio'])
                    if key not in resultsRaw:
                        resultsRaw[key] = []
                    if isWorkloadThreadsTP:
                        resultsRaw[key].append(int(values['workloadThreadsThroughput']))
                    else:
                        resultsRaw[key].append(int(values['sizeThreadsThroughput']))
                else:
                    for workloadOpType in WorkloadOpType:
                        key = toStringSplit(values['name'], values['nWorkloadThreads'], values['nSizeThreads'],
                                            values['initSize'], values['percentageRatio'], workloadOpType)
                        if key not in resultsRaw:
                            resultsRaw[key] = []
                        if workloadOpType == WorkloadOpType.all:
                            resultsRaw[key].append((int(values['ninstrue']) + int(values['ninsfalse']) + int(
                                values['ndeltrue']) + int(values['ndelfalse']) + int(values['ncontainstrue']) + int(
                                values['ncontainsfalse'])) / (float(values['totalelapsedinstime']) + float(
                                values['totalelapseddeltime']) + float(values['totalelapsedcontainstime'])))
                        elif workloadOpType == WorkloadOpType.insert:
                            resultsRaw[key].append(
                                (int(values['ninstrue']) + int(values['ninsfalse'])) / float(values['totalelapsedinstime']))
                        elif workloadOpType == WorkloadOpType.delete:
                            resultsRaw[key].append(
                                (int(values['ndeltrue']) + int(values['ndelfalse'])) / float(values['totalelapseddeltime']))
                        else:  # workloadOpType == WorkloadOpType.contains
                            resultsRaw[key].append((int(values['ncontainstrue']) + int(values['ncontainsfalse'])) / float(
                                values['totalelapsedcontainstime']))
    except Exception as e:
        logging.error(f"Error processing {path}: {e}")
        return

    # Calculate statistics and save to file
    if isWorkloadThreadsTP:
        divideBy = 1000000.0
    else:
        divideBy = 1000.0

    with open(os.path.join(os.path.dirname(path), os.path.basename(path)[:-len('.csv')] + '_statistics.csv'), 'w',
              newline='') as statisticsFile:
        writer = csv.writer(statisticsFile)
        writer.writerow(['benchmark', 'meanTP', 'stddev', 'CV'])
        for key in resultsRaw:
            resultsAll = resultsRaw[key]
            resultsExcludingWarmup = resultsAll[warmupRepeats:]
            results[key] = avg(resultsExcludingWarmup)
            stddev[key] = st.pstdev(resultsExcludingWarmup)
            if results[key] < 1e-8:
                CV = -1
            else:
                CV = stddev[key] / results[key]
            writer.writerow([key, "%.3f" % results[key], "%.3f" % stddev[key], "%.3f" % CV])
            if not isSplitByOpType:
                results[key] /= divideBy

