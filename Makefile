.PHONY: run build test clean

run: build
	mvn javafx:run

build:
	mvn compile

test:
	mvn test

clean:
	mvn clean
