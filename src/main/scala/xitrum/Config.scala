package xitrum

import java.io.File
import java.nio.charset.Charset

import com.hazelcast.client.HazelcastClient
import com.hazelcast.core.{Hazelcast, HazelcastInstance}

import xitrum.scope.session.SessionStore
import xitrum.util.Loader

object Config extends Logger {
  val isProductionMode = (System.getProperty("xitrum.mode") == "production")

  // See xitrum.properties
  // Below are all "val"s

  val properties = {
    try {
      Loader.propertiesFromClasspath("xitrum.properties")
    } catch {
      case _ =>
        try {
          Loader.propertiesFromFile("config/xitrum.properties")
        } catch {
          case _ =>
            logger.error("Could not load xitrum.properties from CLASSPATH or from config/xitrum.properties")
            System.exit(-1)
            null
        }
    }
  }

  val httpPort = properties.getProperty("http_port").toInt

  val proxyIpso: Option[Array[String]] = {
    val s = properties.getProperty("proxy_ips")
    if (s == null) None else Some(s.split(",").map(_.trim))
  }

  val baseUri = properties.getProperty("base_uri", "")

  val compressResponse = {
    val s = properties.getProperty("compress_response")
    if (s == null || s == "false") false else true
  }

  val hazelcastInstance: HazelcastInstance = {
    val hazelcastMode = properties.getProperty("hazelcast_mode", "cluster_member")

    // http://code.google.com/p/hazelcast/issues/detail?id=94
    // http://code.google.com/p/hazelcast/source/browse/trunk/hazelcast/src/main/java/com/hazelcast/logging/Logger.java
    System.setProperty("hazelcast.logging.type", "slf4j")

    // http://www.hazelcast.com/documentation.jsp#SuperClient
    if (hazelcastMode == "super_client")
      System.setProperty("hazelcast.super.client", "true")

    // http://code.google.com/p/hazelcast/wiki/Config
    // http://code.google.com/p/hazelcast/source/browse/trunk/hazelcast/src/main/java/com/hazelcast/config/XmlConfigBuilder.java
    if (hazelcastMode == "super_client" || hazelcastMode == "cluster_member") {
      val config = System.getProperty("user.dir") + File.separator + "config" + File.separator + "hazelcast_cluster_member_or_super_client.xml"
      System.setProperty("hazelcast.config", config)
      Hazelcast.getDefaultInstance
    } else {
      val props = Loader.propertiesFromClasspath("hazelcast_java_client.properties")
      val groupName     = props.getProperty("group_name")
      val groupPassword = props.getProperty("group_password")
      val addresses     = props.getProperty("addresses").split(",").map(_.trim)
      HazelcastClient.newHazelcastClient(groupName, groupPassword, addresses:_*)
    }
  }

  val sessionStore  = {
    val className = properties.getProperty("session_store")
    Class.forName(className).newInstance.asInstanceOf[SessionStore]
  }

  val sessionCookieName = properties.getProperty("session_cookie_name", "_session")

  val secureKey = properties.getProperty("secure_key")

  //----------------------------------------------------------------------------

  // Below are all "var"s so that application developers may change the defaults

  var maxRequestContentLengthInMB = 32  // Same as GAE

  /**
   * For speed, to avoid checking file existance on every request, public files
   * should have URL pattern /public/...
   *
   * favicon.ico: http://en.wikipedia.org/wiki/Favicon
   * robots.txt:  http://en.wikipedia.org/wiki/Robots_exclusion_standard
   */
  var publicFilesNotBehindPublicUrl = List("favicon.ico", "robots.txt")

  /**
   * Xitrum can serve static files (request URL in the form /public/...
   * or /responses/public/... or there is X-Sendfile in the response header),
   * and it caches small static files in memory.
   */
  var cacheSmallStaticFileMaxSizeInKB = 512

  /**
   * Xitrum checks the response Content-Type header to test if the response is
   * textual (text/html, text/plain etc.). If the response is big and gzip or
   * deflate Accept-Encoding header is set in the request, Xitrum will gzip or
   * deflate it. Xitrum compresses both static (see cacheSmallStaticFileMaxSizeInKB)
   * and dynamic response.
   */
  var compressBigTextualResponseMinSizeInKB = 10

  var paramCharsetName = "UTF-8"
  var paramCharset     = Charset.forName(paramCharsetName)

  /**
   * Parameters are logged to access log
   * Comma separated list of sensitive parameters that should not be logged
   */
  var filteredParams = Array("password")

  /**
   * When there is trouble (high load on startup ect.), the response may not be
   * OK. If the response is specified to be cached, we should only cache it
   * for a short time.
   */
  var non200ResponseCacheTTLInSecs = 30
}
