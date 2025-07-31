#!/bin/sh

set -e

JAVA_HOME=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
BUILD_DIR=build
NATIVE_DIR=./nativecode
LIB_NAME=libmembarrier.so
PACKAGE_MAIN=measurements.Main  # your main class
JAR_NAME=experiments_instr.jar

echo "Cleaning build directory..."
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

echo "Compiling Java sources from current dir and nativeCode/, generating JNI header into nativeCode/..."
javac --add-exports java.base/jdk.internal.vm.annotation=ALL-UNNAMED \
  -h "$NATIVE_DIR" -g -d "$BUILD_DIR" \
  $(find . -name "*.java") $(find "$NATIVE_DIR" -name "*.java")

echo "Compiling native JNI library..."
gcc -fPIC -shared -o "$BUILD_DIR/$LIB_NAME" \
  -I"$JAVA_HOME/include" \
  -I"$JAVA_HOME/include/linux" \
  "$NATIVE_DIR/membarrier.c"

echo "Packaging classes into JAR..."
cd "$BUILD_DIR"
echo "Main-class: $PACKAGE_MAIN" > manifest.mf
jar cfm "$JAR_NAME" manifest.mf $(find . -name "*.class")
cd ..

echo ""
echo "✅ Build complete."
echo "Run with:"
echo "java -Djava.library.path=$BUILD_DIR -cp $BUILD_DIR/$JAR_NAME $PACKAGE_MAIN"
