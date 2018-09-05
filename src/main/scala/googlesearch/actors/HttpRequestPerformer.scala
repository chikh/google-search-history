package googlesearch.actors

import akka.actor.{Actor, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.pipe

import scala.concurrent.{ExecutionContext, Future}

object HttpRequestPerformer {
  def props(httpClient: HttpRequest => Future[HttpResponse]): Props =
    Props(new HttpRequestPerformer(httpClient))

  case class Get(requestId: String, r: HttpRequest)

  case class HttpRequestResult(requestId: String, response: HttpResponse)

}

class HttpRequestPerformer(
  httpClient: HttpRequest => Future[HttpResponse]
) extends Actor {

  import HttpRequestPerformer._

  override def receive: Receive = {
    case Get(requestId, request) =>
      val originalSender = sender()

      implicit val contextForWrappingResponse: ExecutionContext =
        context.dispatcher

      httpClient(request)
        .map(HttpRequestResult(requestId, _))
        .pipeTo(originalSender)
        .foreach(_ => context.stop(self))
  }
}
