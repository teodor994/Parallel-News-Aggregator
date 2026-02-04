JAVAC = javac
JAVA = java

CP = .:jackson-core.jar:jackson-databind.jar:jackson-annotations.jar

build:
	$(JAVAC) -cp $(CP) *.java

run:
	$(JAVA) -cp $(CP) Tema1 $(ARGS)

clean:
	rm -f *.class *.txt

.PHONY: build run clean