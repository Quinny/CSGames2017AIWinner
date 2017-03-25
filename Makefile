all:
	javac Client.java

run: all
	java Client fucker &
	java Client fucker2 > /dev/null
