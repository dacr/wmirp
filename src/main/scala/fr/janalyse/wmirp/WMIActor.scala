package fr.janalyse.wmirp

import akka.actor.Actor
import concurrent._
import concurrent.duration._
import concurrent.ExecutionContext.Implicits.global
import akka.actor.Props
import com.typesafe.scalalogging.slf4j.Logging

object WMIActor {
  trait WMIMessage
  object WMIListRequest extends WMIMessage
  case class WMIList(classes: List[ComClass]) extends WMIMessage

  def props =
    Props(new WMIActor)
}

class WMIActor extends Actor with Logging {
  import WMIActor._

  var wmi: WMI = _

  var classes = future { wmi.getPerfClasses }

  override def preStart() {
    wmi = new WMI {}
  }

  override def postStop() {
    wmi.close()
  }

  def receive = {
    case WMIListRequest =>
      println(s"WMIListRequest - completed = ${classes.isCompleted}")
      classes.map { case x =>
        println(x.size)
        sender ! WMIList(x)
      }
  }
}
