package fr.janalyse.wmirp

import akka.io.IO
import spray.can.Http
import akka.actor._
import spray.http._
import HttpMethods._
import akka.event.Logging
import com.typesafe.scalalogging.slf4j.Logging
import java.net.InetSocketAddress
import akka.io.Tcp
import akka.util.Timeout
import akka.pattern.ask
import concurrent._
import concurrent.duration._
import scala.util.{Success,Failure}

object ListenerActor {
  def props(wmiactor:ActorRef) = Props(new ListenerActor(wmiactor))
}

class ListenerActor(wmiactor:ActorRef) extends Actor with Logging {
  def receive = {
    // ------------------------------------------------------------------
    case Http.Connected(remote, _) =>
      sender ! Http.Register(context.actorOf(WMIConnectionHandlerActor.props(remote, sender, wmiactor)))
    // ------------------------------------------------------------------
  }
}

object WMIConnectionHandlerActor {
  def props(remote: InetSocketAddress, connection: ActorRef, wmiactor: ActorRef) =
    Props(new WMIConnectionHandlerActor(remote, connection, wmiactor))
}

class WMIConnectionHandlerActor(remote: InetSocketAddress, connection: ActorRef, wmiactor: ActorRef) extends Actor with Logging {
  import WMIActor._
  import context.dispatcher // The execution context
  
  def receive = {
    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      sender() ! HttpResponse(entity = "PONG")
    // ------------------------------------------------------------------
    case HttpRequest(GET, Uri.Path("/"), _, _, _) =>
      implicit val timeout = Timeout(20 seconds)
      val fstate = wmiactor ? WMIStatusRequest
      val caller=sender
      fstate.onComplete {
        case Failure(ex) => caller!HttpResponse(entity=ex.getMessage)
        case Success(state:WMIStatus)=>
          caller ! HttpResponse(entity = state.toString)
        case Success(other) => caller!HttpResponse(entity="Not understood")
      }
    // ------------------------------------------------------------------
    case HttpRequest(GET, Uri.Path("/list"), _, _, _) =>
      implicit val timeout = Timeout(20 seconds)
      val flist = wmiactor ? WMIListRequest
      val caller=sender
      flist.onComplete {
        case Failure(ex) => caller!HttpResponse(entity=ex.getMessage)
        case Success(wmil:WMIList) =>
          val resp = for { cl <- wmil.classes } yield {cl.name}
          caller ! HttpResponse(entity = resp.mkString("\n"))
        case Success(other) => caller!HttpResponse(entity="Not understood")
      }
    // ------------------------------------------------------------------
    case HttpRequest(m, rq, _, _, _) =>
      logger.debug(s"Not supported request ${m.toString} ${rq.toString}")
      sender() ! HttpResponse(
        status = 404,
        entity = "NotFound")
    // ------------------------------------------------------------------    
    case _: Tcp.ConnectionClosed =>
      logger.debug("Stopping, because connection for remote address {} closed", remote)
      context.stop(self)
    // ------------------------------------------------------------------    
    case Terminated(`connection`) =>
      logger.debug("Stopping, because connection for remote address {} died", remote)
      context.stop(self)
      
  }
}

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()

    val wmiactor = system.actorOf(WMIActor.props)
    val myListener = system.actorOf(ListenerActor.props(wmiactor))

    IO(Http) ! Http.Bind(myListener, interface = "localhost", port = 9900)
  }
}
