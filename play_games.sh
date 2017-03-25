unset PYTHONPATH

for i in 1 2 3 4 5 6 7 8 9 10
do
  ./run.sh &
  python3 ../csgames_2017_ai/src/client.py
  read -p "Press enter to continue"
done
