"""Configuration used by the measurement scripts."""

GRAPH_DIR = "graphs"
DATA_DIR = "results"

# Baseline sequential implementations used for comparison
baselineDataStructures = ["HashTable", "BST", "SkipList"]

# All data-structure variants evaluated in the paper
dataStructures = [
    "HashTable", "SizeHashTable", "OptimisticSizeHashTable", "HandshakesHashTable", "StampedLockHashTable",
    "SkipList", "SizeSkipList", "OptimisticSizeSkipList", "HandshakesSkipList", "StampedLockSkipList",
    "BST", "SizeBST", "OptimisticSizeBST", "HandshakesBST", "StampedLockBST",
]
