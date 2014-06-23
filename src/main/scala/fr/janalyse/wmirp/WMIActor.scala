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
import akka.dispatch.RequiresMessageQueue
import akka.dispatch.BoundedMessageQueueSemantics

object WriterActor {
  def props(destFile: => File) = Props(new WriterActor(destFile))
}

class WriterActor(destFile: => File)
  extends Actor
  with RequiresMessageQueue[BoundedMessageQueueSemantics] {
  
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
      val id = instance.comClass.name + instance.name.map("/" + _).getOrElse("/@")
      output.println(s"$timestamp $id (${duration}ms)")
      for { (key, value) <- entries } {
        output.println(s"\t$key=$value")
      }
    //output.flush() 
  }
}

object WMIWorkerActor {
  trait WMIWorkerMessage
  case class WMIWorkerNumEntriesRequest(instance: ComInstance) extends WMIWorkerMessage
  case class WMIWorkerNumEntries(
    instance: ComInstance,
    entries: Map[String, BigInt],
    timestamp: Long,
    duration: Long) extends WMIWorkerMessage
  case class WMIWorkerDumpTo(
    toWriter: ActorRef,
    comClass: ComClass,
    instanceFilter: (ComInstance) => Boolean,
    useCache: Boolean) extends WMIWorkerMessage
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
    if (wmi!=null) wmi.close()
  }

  def mkWMIWorkerNumEntries(instance: ComInstance) = {
    try {
      val started = System.currentTimeMillis
      val entries = instance.longEntries
      val duration = System.currentTimeMillis - started
      Some(WMIWorkerNumEntries(instance, entries, started, duration))
    } catch {
      case x: Exception => None //TODO
    }
  }

  var instancesCache = Map.empty[ComClass, List[ComInstance]]

  def receive = {
    case WMIWorkerNumEntriesRequest(instance) =>
      mkWMIWorkerNumEntries(instance).foreach { msg => sender ! msg }

    case WMIWorkerDumpTo(toWriter, comClass, instanceFilter, useCache) =>
      val instances = if (useCache) {
        instancesCache
          .get(comClass)
          .getOrElse {
            val found = comClass.instances.filter(instanceFilter)
            instancesCache += comClass -> found
            found
          }
      } else {
        comClass.instances
      }
      instances.foreach { instance =>
        mkWMIWorkerNumEntries(instance).foreach { msg => toWriter ! msg }
      }
  }
}

object WMIActor {
  trait WMIMessage

  object WMIListRequest extends WMIMessage
  case class WMIList(classes: List[ComClass]) extends WMIMessage

  object WMIStatusRequest extends WMIMessage
  case class WMIStatus(
    classesCount: BigInt,
    threadsCount: BigInt,
    processesCount: BigInt) extends WMIMessage {
    override def toString() = {
      s"""Status :
         | Perf. classes count       : $classesCount
         | OS active threads count   : $threadsCount
         | OS active processes count : $processesCount
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

  lazy val processor = wmi.getInstance("""Win32_PerfRawData_PerfOS_Processor""", "_Total")
  lazy val system = wmi.getInstance("Win32_PerfRawData_PerfOS_System")

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
    classesFuture = future { wmi.getPerfClasses.filter(_.instances.size > 0) }
  }

  override def postStop() {
    if (wmi!=null) wmi.close()
  }

  val workers = context.actorOf(
    WMIWorkerActor.props.withRouter(SmallestMailboxRouter(4)),
    "workersRouter")

  def receive = {
    case WMIStatusRequest =>
      implicit val timeout = Timeout(20 seconds)
      val fsys = workers ? WMIWorkerNumEntriesRequest(system.get)
      val caller = sender()
      for {
        WMIWorkerNumEntries(_, sysstate, _, _) <- fsys
        classescount <- classesFuture.map(_.size)
      } {
        caller ! WMIStatus(
          classesCount = classescount,
          threadsCount = sysstate.get("Threads").getOrElse(BigInt(-1)),
          processesCount = sysstate.get("Processes").getOrElse(BigInt(-1)))
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
        def genOutputFile: File = {
          val filesdf = new java.text.SimpleDateFormat("yyMMdd_HHmmss")
          val hostname = java.net.InetAddress.getLocalHost.getHostName
          val ts = filesdf.format(new java.util.Date())
          new File(s"metrics_${hostname}_${ts}.log")
        }
        val writer = context.actorOf(WriterActor.props(genOutputFile))

        val highfreqmonitor = context.actorOf(
          WMIMonitorActor.props(
            wmiWorkers = workers,
            writerActor = writer,
            delay = 10.seconds,
            tofollow = singletons2follow,
            useCache = true))
        highfreqmonitor ! WMIMonitorActor.Tick

        def lowInstFilter(inst: ComInstance): Boolean = {
          inst
            .name
            .filter(n =>
              (n == "_Total") || (n == "Total") || (n contains "Global"))
            .isDefined
        }

        val lowfreqmonitor = context.actorOf(
          WMIMonitorActor.props(
            wmiWorkers = workers,
            writerActor = writer,
            delay = 20.seconds,
            tofollow = otherClasses,
            instanceFilter = lowInstFilter,
            useCache = true))
        lowfreqmonitor ! WMIMonitorActor.Tick

        def verylowInstFilter(inst: ComInstance): Boolean = {
          !lowInstFilter(inst) && !inst.comClass.name.contains("PagingFile")
        }

        val verylowfreqmonitor = context.actorOf(
          WMIMonitorActor.props(
            wmiWorkers = workers,
            writerActor = writer,
            delay = 30.seconds,
            tofollow = otherClasses,
            instanceFilter = verylowInstFilter,
            useCache = false))
        verylowfreqmonitor ! WMIMonitorActor.Tick

      }
  }
}

object WMIMonitorActor {
  object Tick
  def props(
    wmiWorkers: ActorRef,
    writerActor: ActorRef,
    delay: FiniteDuration,
    tofollow: Iterable[ComClass],
    instanceFilter: (ComInstance) => Boolean = _ => true,
    useCache: Boolean = true) =
    Props(new WMIMonitorActor(wmiWorkers, writerActor, delay, tofollow, instanceFilter, useCache))
}

class WMIMonitorActor(
  wmiWorkers: ActorRef,
  writerActor: ActorRef,
  delay: FiniteDuration,
  tofollow: Iterable[ComClass],
  instanceFilter: (ComInstance) => Boolean,
  useCache: Boolean) extends Actor {
  import WMIMonitorActor._
  import WMIWorkerActor._
  import context.dispatcher

  def receive = {
    case Tick =>
      for { cl <- tofollow } {
        wmiWorkers ! WMIWorkerDumpTo(writerActor, cl, instanceFilter, useCache)
      }
      context.system.scheduler.scheduleOnce(delay, self, Tick)
  }
}
