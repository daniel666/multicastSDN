#! /usr/bin/python
from subprocess import call
import time

addhost=[]
removehost=[]

for i in range(1,3):
	addhost.append("h%s" % i)
for i in range(1,30):
	removehost.append("h%s" % i)
for host in addhost:
	call([host, 'iperf', '-s', '-u', '-B', '224.0.55.55'])
	time.sleep(3)
	
print "hello"
