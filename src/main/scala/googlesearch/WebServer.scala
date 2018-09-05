package googlesearch

import java.util.UUID

import akka.Done
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{path, _}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import googlesearch.actors.{GoogleRequestActor, SearchHistoryActor, SearchSupervisor}
import org.slf4j.{Logger, LoggerFactory}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object WebServer extends App {
  val log: Logger = LoggerFactory.getLogger(this.getClass)

  case class SearchResult(urls: Seq[String])
  case class SearchHistory(queries: Seq[String])

  implicit val searchResultFormat = jsonFormat1(SearchResult)
  implicit val searchHistoryFormat = jsonFormat1(SearchHistory)

  implicit val system = ActorSystem()
  implicit val timeout: Timeout = Configuration.webRequestTimeout.seconds
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http(system)

  val searchSupervisor = system.actorOf(
    SearchSupervisor.props(request => http.singleRequest(request)),
    "searchSupervisor"
  )

  val route =
    pathPrefix("search") {
      (get & path(Segment)) { (query) =>
        val requestId = UUID.randomUUID().toString

        onComplete(searchSupervisor ? GoogleRequestActor.SearchFor(requestId, query)) {
          case Success(GoogleRequestActor.SearchResults(`requestId`, urls)) =>
            complete(SearchResult(urls))
          case Success(GoogleRequestActor.QueryLimitExceeded(_)) =>
            complete(StatusCodes.TooManyRequests)
          case Success(GoogleRequestActor.SearchError(code)) =>
            complete(code)
          case Failure(e) => failWith(e)
        }
      }
    } ~ path("history") {
      get {
        val requestId = UUID.randomUUID().toString

        complete((searchSupervisor ? SearchHistoryActor.Remind(requestId)) map {
          case SearchHistoryActor.Remembered(`requestId`, queries) => SearchHistory(queries.seq)
        })
      }
    }

  val host = Configuration.host
  val port = Configuration.port

  log.debug("Start binding the routes")

  val bindingFuture = Http().bindAndHandle(
    handler = route,
    interface = host,
    port = port
  )

  bindingFuture.foreach(_ =>
    log.info("HTTP server is bound to {}:{}", host, port))

  private val shutdown = CoordinatedShutdown(system)

  shutdown.addTask(
    CoordinatedShutdown.PhaseBeforeServiceUnbind,
    "log-shutdown-started") { () =>
      Future.successful {
        log.warn("Application shutdown is triggered.")
        Done
      }
    }

  shutdown.addTask(
    CoordinatedShutdown.PhaseServiceUnbind,
    "shutdown-connection-pool") { () =>
      bindingFuture.flatMap(_.unbind).flatMap { _ =>
        Http().shutdownAllConnectionPools
      }.map { _ =>
        log.debug("Shutdown of the connection pool is finished")
        Done
      }
    }
}
