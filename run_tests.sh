printf "Testing...\n"
cd build
java -Djava.library.path=. -server -ea -Xms1G -Xmx1G -jar experiments_instr.jar test
cd ..
printf "Finished testing\n"
