package fr.janalyse.wmirp

import akka.io.IO
import spray.can.Http
import akka.actor._
import spray.http._
import HttpMethods._

object ListenerActor {
  def props() = Props(new ListenerActor)
}

class ListenerActor extends Actor {
  def receive = {
    // ------------------------------------------------------------------
    case _: Http.Connected =>
      sender ! Http.Register(self)
    // ------------------------------------------------------------------
    case HttpRequest(GET, Uri.Path("/ping"), _, _, _) =>
      sender() ! HttpResponse(entity = "PONG")
    // ------------------------------------------------------------------
    case HttpRequest(GET, Uri.Path("/list"), _, _, _) =>
      new WMI {}
      sender() ! HttpResponse(
        entity = "PONG"
      )
    // ------------------------------------------------------------------
    case HttpRequest(_, _, _, _, _) =>
      sender() ! HttpResponse(
        status = 404,
        entity = "NotFound")
    // ------------------------------------------------------------------
  }
}

object Main {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem()

    val myListener: ActorRef = system.actorOf(ListenerActor.props)

    IO(Http) ! Http.Bind(myListener, interface = "localhost", port = 9900)
  }
}
