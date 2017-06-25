package googlesearch

import com.typesafe.config._

object Configuration {
  private val confPathPrefix = "google-search-cache"
  private lazy val conf = ConfigFactory.load()

  lazy val host: String = conf.getString(s"$confPathPrefix.server-host")
  lazy val port: Int = conf.getInt(s"$confPathPrefix.server-port")
  lazy val searchUrlPrefix: String = conf.getString(s"$confPathPrefix.search-url-prefix")
  lazy val searchQuotaPeriod: Int =
    conf.getInt(s"$confPathPrefix.search-requests-quota.period-minutes")
  lazy val searchQuotaRequests: Int =
    conf.getInt(s"$confPathPrefix.search-requests-quota.requests-count")
}
