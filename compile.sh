#!/bin/sh

# Clean build directory and compile Java sources
rm -rf build
mkdir build

java="java"
javac="javac"
jar="jar"

echo "COMPILING JAVA CLASSES..."
cmd="$javac --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED -g -d build `find . -name *.java`"
echo $cmd
$cmd

cd build

if [ "$?" -eq "0" ]; then
	echo "Main-class: measurements.Main" > manifest.mf
	$jar cfm experiments_instr.jar manifest.mf *
else
	echo "ERROR compiling."
fi
