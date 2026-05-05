.PHONY: run build test benchmark benchmark-gc clean

run: build
	mvn javafx:run

build:
	mvn compile

test:
	mvn test

benchmark: build
	mvn exec:java -Dexec.mainClass="com.sismd.benchmark.BenchmarkRunner"

benchmark-gc: build
	./benchmark-gc.sh

clean:
	mvn clean
