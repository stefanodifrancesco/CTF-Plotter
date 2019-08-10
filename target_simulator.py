import time
import random

def main():
    
    for i in range(20000):
        f=open("log.txt", "a+")
        id = random.randint(1, 4294967295)
        ts = random.randint(200000, 900000)
        line = str(id) + " " + str(ts) + '\n'
        print(line)
        f.write(line)
        time.sleep(0.001)
        f.close()

main()