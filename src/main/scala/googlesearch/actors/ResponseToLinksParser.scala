package googlesearch.actors

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.model.HttpEntity
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.ExecutionContext

object ResponseToLinksParser {
  def props: Props = Props(new ResponseToLinksParser)

  case class ParseForLinks(
    requestId: String,
    originalRequester: ActorRef,
    response: HttpEntity
  )

  case class SearchResults(requestId: String, urls: Seq[String])

}

class ResponseToLinksParser extends Actor {

  import ResponseToLinksParser._

  override def receive: Receive = {
    case ParseForLinks(requestId, originalRequester, response) =>
      implicit val ec: ExecutionContext =
        context.system.dispatchers.lookup("parser-dispatcher")

      implicit val materializer: ActorMaterializer = ActorMaterializer()

      // TODO: use another Materializer (thread pool) for folding bytes
      response.dataBytes.runFold(ByteString(""))(_ ++ _).map { body =>
        val hrefRegexp = """href=\"(http[s]{0,1}:\/\/[^"]*)\"""".r
        val links =
          hrefRegexp.findAllMatchIn(body.utf8String).map(_.group(1)).toVector

        originalRequester ! SearchResults(requestId, links)

        context.stop(self)
      }
  }
}
