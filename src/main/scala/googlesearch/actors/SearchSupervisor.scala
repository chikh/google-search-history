package googlesearch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import googlesearch.actors.QuotaAwareSearchRequestManger.SearchFor
import googlesearch.actors.SearchHistoryActor.Remind

import scala.concurrent.Future

object SearchSupervisor {
  def props(httpClient: HttpRequest => Future[HttpResponse]): Props =
    Props(new SearchSupervisor(httpClient))
}

class SearchSupervisor(
  httpClient: HttpRequest => Future[HttpResponse]
) extends Actor {

  val historyActor: ActorRef =
    context.system.actorOf(SearchHistoryActor.props, "history")

  val requestActor: ActorRef =
    context.system.actorOf(QuotaAwareSearchRequestManger.props(historyActor, httpClient))

  override def receive: Receive = {
    case m: SearchFor => requestActor forward m
    case m: Remind => historyActor forward m
  }
}
