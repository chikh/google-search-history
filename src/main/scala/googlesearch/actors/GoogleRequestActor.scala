package googlesearch.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import akka.util.ByteString
import googlesearch.actors.GoogleRequestActor._

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Performs google search request and returns all first page URLs.
  *
  * <h1>Important!</h1>
  * There is the limitation on Google search queries per time period
  * (see <a href="https://stackoverflow.com/a/22703153/1218116"> this answer on
  * SO</a>). So there is should be one `this` actor per one node having unique
  * IP.
  *
  * @param searchHistory [[googlesearch.actors.SearchHistoryActor]]
  * @param httpClient HTTP GET request service
  * @param initialQuota Google's limits configuration
  */
class GoogleRequestActor(
                          searchHistory: ActorRef,
                          httpClient: HttpRequest => Future[HttpResponse],
                          initialQuota: GoogleQueryQuota =
                            GoogleQueryQuota(15, 1.hour)
                        ) extends Actor with ActorLogging {
  import akka.pattern.pipe
  import context.dispatcher

  final implicit val materializer: ActorMaterializer =
    ActorMaterializer(ActorMaterializerSettings(context.system))

  def receive: Receive = handleWithState(initialQuota, None, Map.empty, Map.empty)

  // TODO: Refactor this into two actors: one for quota handling and one for http requests
  def handleWithState(
                       quota: GoogleQueryQuota,
                       quotaResetTimer: Option[Cancellable],
                       requestIdToSender: Map[String, ActorRef],
                       historyRequestIdToQuery: Map[String, String]
                     ): Receive = {
    case SearchFor(requestId, query) if quota.requestsLeft > 0 =>
      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender + (requestId -> sender()),
        historyRequestIdToQuery + (requestId -> query)
      ))

      searchHistory ! SearchHistoryActor.RememberString(requestId, query)

    case SearchFor(requestId, _) => sender() ! QueryLimitExceeded(requestId)

    case SearchHistoryActor.AcknowledgeRemembering(requestId) if quota.requestsLeft > 0 =>
      val searchQuery = historyRequestIdToQuery(requestId)

      context.become(handleWithState(
        quota.copy(requestsLeft = quota.requestsLeft - 1),
        quotaResetTimer.orElse(Some(
          context.system.scheduler.scheduleOnce(quota.replenishPeriod, self, ResetQuota)
        )),
        requestIdToSender,
        historyRequestIdToQuery - requestId
      ))

      httpClient(HttpRequest(
        uri = s"https://www.google.ru/search?q=$searchQuery"
      )).map(HttpRequestResult(requestId, _)).pipeTo(self)

    case SearchHistoryActor.AcknowledgeRemembering(requestId) =>
      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery - requestId
      ))

      requestIdToSender(requestId) ! QueryLimitExceeded(requestId)

    case HttpRequestResult(requestId, HttpResponse(StatusCodes.OK, _, entity, _)) =>
      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery
      ))

      entity.dataBytes.runFold(ByteString(""))(_ ++ _).map { body =>
        val hrefRegexp = """href=\"(http[s]{0,1}:\/\/[^"]*)\"""".r
        val links = hrefRegexp.findAllMatchIn(body.utf8String).map(_.group(1)).toVector

        requestIdToSender(requestId) ! SearchResults(requestId, links)
      }

    case HttpRequestResult(requestId, resp @ HttpResponse(code, _, _, _)) =>
      resp.discardEntityBytes()

      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery
      ))

      requestIdToSender(requestId) ! SearchError(code)

    case ResetQuota => context.become(handleWithState(
      initialQuota,
      None,
      requestIdToSender,
      historyRequestIdToQuery
    ))
  }
}

object GoogleRequestActor {
  case class SearchFor(requestId: String, query: String)
  case class SearchResults(requestId: String, urls: Seq[String])
  case class QueryLimitExceeded(requestId: String)
  case class SearchError(code: StatusCode)
  case class HttpRequestResult(requestId: String, response: HttpResponse)
  case object ResetQuota

  def props(
             searchHistory: ActorRef,
             httpClient: HttpRequest => Future[HttpResponse]
           ): Props =
    Props(new GoogleRequestActor(searchHistory, httpClient))
}

case class GoogleQueryQuota(requestsLeft: Int, replenishPeriod: FiniteDuration)
