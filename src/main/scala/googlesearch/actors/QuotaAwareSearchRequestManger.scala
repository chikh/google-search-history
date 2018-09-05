package googlesearch.actors

import akka.actor.{Actor, ActorRef, Cancellable, Props}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse, StatusCode, StatusCodes}
import akka.stream.ActorMaterializer
import googlesearch.Configuration._
import googlesearch.actors.QuotaAwareSearchRequestManger._
import googlesearch.actors.HttpRequestPerformer.HttpRequestResult

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Performs google search request and returns all first page URLs. Implementation isn't save from
  * duplicating requests and responses.
  *
  * <h1>Important!</h1>
  * There is the limitation on Google search queries per time period
  * (see <a href="https://stackoverflow.com/a/22703153/1218116"> this answer on
  * SO</a>). So there is should be one `this` actor per one node having unique
  * IP.
  *
  * @param searchHistory Ref to [[googlesearch.actors.SearchHistoryActor]]
  * @param httpClient    HTTP GET request service
  * @param initialQuota  Google's limits configuration
  */
class QuotaAwareSearchRequestManger(
  searchHistory: ActorRef,
  httpClient: HttpRequest => Future[HttpResponse],
  initialQuota: GoogleQueryQuota =
  GoogleQueryQuota(searchQuotaRequests, searchQuotaPeriod.minutes)
) extends Actor {

  import context.dispatcher

  def receive: Receive =
    handleWithState(initialQuota, None, Map.empty, Map.empty)

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
      // TODO: Save from second acknowledgement with same requestId
      val searchQuery = historyRequestIdToQuery(requestId)

      context.become(handleWithState(
        quota.copy(requestsLeft = quota.requestsLeft - 1),
        quotaResetTimer.orElse(Some(
          context.system.scheduler.scheduleOnce(quota.replenishPeriod, self, ResetQuota)
        )),
        requestIdToSender,
        historyRequestIdToQuery - requestId
      ))

      val searchRequest = HttpRequest(uri = s"$searchUrlPrefix$searchQuery")

      context.actorOf(HttpRequestPerformer.props(httpClient)) !
        HttpRequestPerformer.Get(requestId, searchRequest)

    case SearchHistoryActor.AcknowledgeRemembering(requestId) =>
      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery - requestId
      ))

      // TODO: Save from second acknowledgement with same requestId
      requestIdToSender(requestId) ! QueryLimitExceeded(requestId)

    case HttpRequestResult(requestId, HttpResponse(StatusCodes.OK, _, entity, _)) =>
      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery
      ))

      // TODO: Consider absence of the request
      val originalRequester = requestIdToSender(requestId)

      context.system.actorOf(ResponseToLinksParser.props) !
        ResponseToLinksParser.ParseForLinks(
          requestId,
          originalRequester,
          entity
        )

    case HttpRequestResult(requestId, resp@HttpResponse(code, _, _, _)) =>
      implicit val materializer: ActorMaterializer = ActorMaterializer()

      resp.discardEntityBytes()

      context.become(handleWithState(
        quota,
        quotaResetTimer,
        requestIdToSender - requestId,
        historyRequestIdToQuery
      ))

      // TODO: Consider absence of the request
      requestIdToSender(requestId) ! SearchError(code)

    case ResetQuota => context.become(handleWithState(
      initialQuota,
      None,
      requestIdToSender,
      historyRequestIdToQuery
    ))
  }
}

object QuotaAwareSearchRequestManger {
  case class SearchFor(requestId: String, query: String)
  case class QueryLimitExceeded(requestId: String)
  case class SearchError(code: StatusCode)
  case object ResetQuota

  def props(
    searchHistory: ActorRef,
    httpClient: HttpRequest => Future[HttpResponse]
  ): Props =
    Props(new QuotaAwareSearchRequestManger(searchHistory, httpClient))
}

case class GoogleQueryQuota(requestsLeft: Int, replenishPeriod: FiniteDuration)
