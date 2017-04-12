all:
	javac Client.java

run: all
	java Client player1 &
	java Client player2 > /dev/null
