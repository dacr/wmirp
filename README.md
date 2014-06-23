#wmirp

##Introduction

The goal of wmirp is to generate WMI historical metrics series for windows monitoring purposes. 

It relies on JACOB COM BRIDGE : http://sourceforge.net/projects/jacob-project/

## Quick start

### Install SBT to compile wmirp

http://www.scala-sbt.org/

### Generate the executable jar

sbt -Djava.library.path=src/main/resources/ assembly

### Start wmirp

java -jar target\scala-2.10\wmirp.jar

Once started, wmirp will generate a json like file such as "metrics_EB-OR6103080_140507_091523.log"
which will contain all captured data.

It also starts a embedded http server :

- http://localhost:9900/ the home page
- http://localhost:9900/list to get the list of found WMI perf classes
- http://localhost:9900/quit to end wmirp 

### Start the interactive console

sbt -Djava.library.path=src/main/resources/ console

scala> wmi.getPerfClasses.map(_.name).foreach(println)

scala> wmi.getPerfClasses.map(_.attributes.size).sum

scala> wmi.getPerfClasses.find(_.name contains "Processor").map(_.attributes)

*(:q to exit from the scala console)*