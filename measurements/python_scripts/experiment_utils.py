"""Common helper functions for measurement scripts."""

from pathlib import Path
import os

def clear_previous_results():
    """Remove any CSV results from a previous run."""
    for pattern in ("build/*.csv", "build/*.csv_stdout"):
        for file in Path().glob(pattern):
            file.unlink()

def concat_results(output_path: Path):
    """Concatenate per-run CSV files into ``output_path``."""
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with output_path.open("wb") as out:
        for csv_file in sorted(Path("build").glob("data-*.csv")):
            out.write(csv_file.read_bytes())

def java_cmd(memory_size: str):
    """Return the Java command prefix with the requested memory size."""
    base = (
        "java -server -XX:-RestrictContended -XX:ContendedPaddingWidth=64 "
        f"-Xms{memory_size} -Xmx{memory_size} -jar build/experiments_instr.jar "
    )
    return base

def parse_int_list(value: str):
    """Return a list of integers parsed from a comma-separated string."""
    return [int(x) for x in value.strip("[]").split(",") if x]