package fr.janalyse.wmirp

import akka.actor.Actor
import concurrent._
import concurrent.duration._
//import concurrent.ExecutionContext.Implicits.global
import akka.actor.Props
import com.typesafe.scalalogging.slf4j.Logging
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import akka.pattern.ask

object WMIWorkerActor {
  trait WMIWorkerMessage
  case class WMIWorkerNumEntriesRequest(instance: ComInstance)
  case class WMIWorkerNumEntries(entries: Map[String, Double])
  def props() = Props(new WMIWorkerActor)
}

class WMIWorkerActor extends Actor with Logging {
  import WMIWorkerActor._
  import context.dispatcher // The execution context

  implicit var wmi: WMI = _

  override def preStart() {
    wmi = new WMI {}
  }

  override def postStop() {
    wmi.close()
  }

  def receive = {
    case WMIWorkerNumEntriesRequest(instance) =>
      val entries = instance.numEntries
      sender ! WMIWorkerNumEntries(entries)
  }
}

object WMIActor {
  trait WMIMessage

  object WMIListRequest extends WMIMessage
  case class WMIList(classes: List[ComClass]) extends WMIMessage

  object WMIStatusRequest extends WMIMessage
  case class WMIStatus(
    classesCount: Int,
    threadsCount: Int,
    processesCount: Int,
    percentProcessorTime: Int) extends WMIMessage {
    override def toString() = {
      s"""Status :
         | Perf. classes count       : $classesCount
         | OS active threads count   : $threadsCount
         | OS active processes count : $processesCount
         | Processor cpu time        : $percentProcessorTime %
         """.stripMargin
    }
  }

  object WMIDump extends WMIMessage

  def props =
    Props(new WMIActor)
}

class WMIActor extends Actor with Logging {
  import WMIActor._
  import WMIWorkerActor._

  import context.dispatcher // The execution context

  implicit var wmi: WMI = _

  var classes: Future[List[ComClass]] = _

  lazy val processor = wmi.getInstance("""Win32_PerfFormattedData_PerfOS_Processor""", "_Total")
  lazy val system = wmi.getInstance("Win32_PerfFormattedData_PerfOS_System")

  lazy val instancesNamesAtStart = classes.map { cls =>
    cls.map(cl => cl -> cl.instancesNames).toMap
  }

  // Classes with one unik instance
  lazy val singletonsClasses = instancesNamesAtStart.map {
    _.collect {
      case (cl, Nil) => cl
    }
  }

  lazy val otherClasses = instancesNamesAtStart.map {
    _.collect {
      case (cl, names) if names.size > 0 => cl
    }
  }

  override def preStart() {
    wmi = new WMI {}
    classes = future { wmi.getPerfClasses }
  }

  override def postStop() {
    wmi.close()
  }

  val workers = context.actorOf(
    WMIWorkerActor.props.withRouter(SmallestMailboxRouter(5)),
    "workersRouter")

  def receive = {
    case WMIStatusRequest =>
      implicit val timeout = Timeout(20 seconds)
      val fproc = workers ? WMIWorkerNumEntriesRequest(processor.get)
      val fsys = workers ? WMIWorkerNumEntriesRequest(system.get)
      val caller = sender()
      for {
        WMIWorkerNumEntries(procstate) <- fproc
        WMIWorkerNumEntries(sysstate) <- fsys
        classescount <- classes.map(_.size)
      } {
        caller ! WMIStatus(
          classesCount = classescount,
          threadsCount = sysstate.get("Threads").map(_.toInt).getOrElse(-1),
          processesCount = sysstate.get("Processes").map(_.toInt).getOrElse(-1),
          percentProcessorTime = procstate.get("PercentProcessorTime").map(_.toInt).getOrElse(-1))
      }
    case WMIListRequest =>
      val caller = sender
      classes.onSuccess {
        case x =>
          caller ! WMIList(x)
      }

    case WMIDump =>

  }
}
