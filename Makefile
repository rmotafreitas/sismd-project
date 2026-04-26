.PHONY: run build test benchmark clean

run: build
	mvn javafx:run

build:
	mvn compile

test:
	mvn test

benchmark: build
	mvn exec:java -Dexec.mainClass="com.sismd.benchmark.BenchmarkRunner"

clean:
	mvn clean
