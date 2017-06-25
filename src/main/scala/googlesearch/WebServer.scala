package googlesearch

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{path, _}
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import googlesearch.actors.{GoogleRequestActor, SearchHistoryActor}
import spray.json.DefaultJsonProtocol._

import scala.concurrent.duration._
import scala.io.StdIn
import scala.util.{Failure, Success}

object WebServer extends App {
  case class SearchResult(urls: Seq[String])
  case class SearchHistory(queries: Seq[String])

  implicit val searchResultFormat = jsonFormat1(SearchResult)
  implicit val searchHistoryFormat = jsonFormat1(SearchHistory)

  implicit val system = ActorSystem()
  implicit val timeout: Timeout = 5.seconds
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher

  val http = Http(system)

  // TODO: it is better to have custom supervisor of these two actors
  val historyActor = system.actorOf(SearchHistoryActor.props, "history")
  val requestActor = system.actorOf(GoogleRequestActor.props(
    historyActor, request => http.singleRequest(request)))

  val route =
    pathPrefix("search") {
      (get & path(Segment)) { (query) =>
        val requestId = UUID.randomUUID().toString

        onComplete(requestActor ? GoogleRequestActor.SearchFor(requestId, query)) {
          case Success(GoogleRequestActor.SearchResults(`requestId`, urls)) =>
            complete(SearchResult(urls))
          case Success(GoogleRequestActor.QueryLimitExceeded(_)) =>
            complete(StatusCodes.Forbidden)
          case Success(GoogleRequestActor.SearchError(code)) =>
            complete(code)
          case Failure(e) => failWith(e)
        }
      }
    } ~ path("history") {
      get {
        val requestId = UUID.randomUUID().toString

        complete((historyActor ? SearchHistoryActor.Remind(requestId)) map {
          case SearchHistoryActor.Remembered(`requestId`, queries) => SearchHistory(queries.seq)
        })
      }
    }

  val host = Configuration.host
  val port = Configuration.port

  val bindingFuture = Http().bindAndHandle(route, host, port)

  println(s"Server online at http://$host:$port/\nPress RETURN to stop...")

  StdIn.readLine()
  bindingFuture.flatMap(_.unbind()).onComplete(_ => system.terminate())
}
