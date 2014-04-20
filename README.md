wmirp
=====

WMI HTTP proxy

Rely on JACOB COM BRIDGE : http://sourceforge.net/projects/jacob-project/

sbt -Djava.library.path=src/main/resources/ test
 
sbt -Djava.library.path=src/main/resources/ console

   wmi.getPerfClasses.map(_.name).foreach(println)

   wmi.getPerfClasses.map(_.attributes.size).sum

   wmi.getPerfClasses.find(_.name contains "Processor").map(_.attributes)

