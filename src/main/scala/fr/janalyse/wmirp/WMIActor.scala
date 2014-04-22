package fr.janalyse.wmirp

import akka.actor.Actor
import akka.actor.ActorRef
import concurrent._
import concurrent.duration._
import akka.actor.Props
import com.typesafe.scalalogging.slf4j.Logging
import akka.routing.SmallestMailboxRouter
import akka.util.Timeout
import akka.pattern.ask
import java.io.PrintWriter
import java.io.File

object WriterActor {
  def props(destFile: File) = Props(new WriterActor(destFile))
}

class WriterActor(destFile: File) extends Actor {
  import WMIWorkerActor._

  var output: PrintWriter = _

  override def preStart() {
    output = new PrintWriter(destFile)
  }

  override def postStop() {
    output.close()
  }

  def receive = {
    case WMIWorkerNumEntries(instance, entries, timestamp, duration) =>
      val id = instance.comClass.name + instance.name.map("/" + _)
      output.println(s"$timestamp $id (${duration}ms)")
      for { (key, value) <- entries } {
        output.println(s"\t$key=$value")
      }
  }
}

object WMIWorkerActor {
  trait WMIWorkerMessage
  case class WMIWorkerNumEntriesRequest(instance: ComInstance) extends WMIWorkerMessage
  case class WMIWorkerNumEntries(
    instance: ComInstance,
    entries: Map[String, Double],
    timestamp: Long,
    duration: Long) extends WMIWorkerMessage
  case class WMIWorkerDumpTo(
    toWriter: ActorRef,
    comClass: ComClass) extends WMIWorkerMessage
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

  def mkWMIWorkerNumEntries(instance: ComInstance) = {
    val started = System.currentTimeMillis
    val entries = instance.numEntries
    val duration = System.currentTimeMillis - started
    WMIWorkerNumEntries(instance, entries, started, duration)
  }

  def receive = {
    case WMIWorkerNumEntriesRequest(instance) =>
      sender ! mkWMIWorkerNumEntries(instance)
    case WMIWorkerDumpTo(toWriter, comClass) =>
      comClass.instances.foreach { instance =>
        toWriter ! mkWMIWorkerNumEntries(instance)
      }
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

  object WMIStartMonitor extends WMIMessage

  def props = Props(new WMIActor)
}

class WMIActor extends Actor with Logging {
  import WMIActor._
  import WMIWorkerActor._

  import context.dispatcher // The execution context

  implicit var wmi: WMI = _

  var classesFuture: Future[List[ComClass]] = _

  lazy val processor = wmi.getInstance("""Win32_PerfFormattedData_PerfOS_Processor""", "_Total")
  lazy val system = wmi.getInstance("Win32_PerfFormattedData_PerfOS_System")

  lazy val instancesNamesAtStartFuture = classesFuture.map { cls =>
    cls.map(cl => cl -> cl.instancesNames).toMap
  }

  // Classes with one unik instance
  lazy val singletonsClassesFuture = instancesNamesAtStartFuture.map {
    _.collect {
      case (cl, Nil) => cl
    }
  }

  lazy val otherClassesFuture = instancesNamesAtStartFuture.map {
    _.collect {
      case (cl, names) if names.size > 0 => cl
    }
  }

  override def preStart() {
    wmi = new WMI {}
    classesFuture = future { wmi.getPerfClasses }
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
        WMIWorkerNumEntries(_, procstate, _, _) <- fproc
        WMIWorkerNumEntries(_, sysstate, _, _) <- fsys
        classescount <- classesFuture.map(_.size)
      } {
        caller ! WMIStatus(
          classesCount = classescount,
          threadsCount = sysstate.get("Threads").map(_.toInt).getOrElse(-1),
          processesCount = sysstate.get("Processes").map(_.toInt).getOrElse(-1),
          percentProcessorTime = procstate.get("PercentProcessorTime").map(_.toInt).getOrElse(-1))
      }
    case WMIListRequest =>
      val caller = sender
      classesFuture.onSuccess {
        case x =>
          caller ! WMIList(x)
      }

    case WMIStartMonitor =>
      for {
        singletons2follow <- singletonsClassesFuture
        otherClasses <- otherClassesFuture
      } {
        val hostname = java.net.InetAddress.getLocalHost.getHostName
        val destFile = new File(s"metrics-$hostname.log")
        val writer = context.actorOf(WriterActor.props(destFile))

        val highfreqmonitor = context.actorOf(
          WMIMonitorActor.props(
            workers,
            writer,
            30.seconds,
            singletons2follow
          )
        )
        highfreqmonitor ! WMIMonitorActor.Tick
      }
  }
}

object WMIMonitorActor {
  object Tick
  def props(
    wmiWorkers: ActorRef,
    writerActor: ActorRef,
    delay: FiniteDuration,
    tofollow: Iterable[ComClass]) =
    Props(new WMIMonitorActor(wmiWorkers, writerActor, delay, tofollow))
}

class WMIMonitorActor(
  wmiWorkers: ActorRef,
  writerActor: ActorRef,
  delay: FiniteDuration,
  tofollow: Iterable[ComClass]) extends Actor {
  import WMIMonitorActor._
  import WMIWorkerActor._
  import context.dispatcher

  def receive = {
    case Tick =>
      for { cl <- tofollow } { wmiWorkers ! WMIWorkerDumpTo(writerActor, cl) }
      context.system.scheduler.scheduleOnce(delay, self, "foo")
  }
}
