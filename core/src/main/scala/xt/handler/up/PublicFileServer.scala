package xt.handler.up

import xt.Logger

import java.io.File

import org.jboss.netty.channel.{ChannelHandler, SimpleChannelUpstreamHandler, ChannelHandlerContext, MessageEvent, ExceptionEvent, Channels}
import org.jboss.netty.handler.codec.http.{HttpMethod, HttpResponseStatus, HttpVersion, DefaultHttpResponse, HttpHeaders}
import ChannelHandler.Sharable
import HttpMethod._
import HttpResponseStatus._
import HttpVersion._

/**
 * Serves special files or files in /public directory.
 *
 * Special files:
 *    favicon.ico may be not at the root: http://en.wikipedia.org/wiki/Favicon
 *    robots.txt     must be at the root: http://en.wikipedia.org/wiki/Robots_exclusion_standard
 */
@Sharable
class PublicFileServer extends SimpleChannelUpstreamHandler with Logger {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val m = e.getMessage
    if (!m.isInstanceOf[UriParserResult]) {
      ctx.sendUpstream(e)
      return
    }

    val upr     = m.asInstanceOf[UriParserResult]
    val request = upr.request
    val decoded = upr.pathInfo.decoded

    if (request.getMethod != GET) {
      Channels.fireMessageReceived(ctx, upr)
      return
    }

    val decoded2 = if (decoded == "/favicon.ico" || decoded == "/robots.txt")
      "/public" + decoded
    else
      decoded

    if (!decoded2.startsWith("/public/")) {
      Channels.fireMessageReceived(ctx, upr)
      return
    }

    val response = new DefaultHttpResponse(HTTP_1_1, OK)
    sanitizePathInfo(decoded2) match {
      case Some(abs) =>
        response.setHeader("X-Sendfile", abs)
        ctx.getChannel.write((request, response))

      case None =>
        response.setStatus(NOT_FOUND)
        HttpHeaders.setContentLength(response, 0)
        ctx.getChannel.write(response)
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
    logger.error("PublicFileServer", e.getCause)
    e.getChannel.close
  }

  //----------------------------------------------------------------------------

  /**
   * @return None if pathInfo is invalid or the corresponding file is hidden,
   *         otherwise Some(the absolute file path)
   */
  private def sanitizePathInfo(pathInfo: String): Option[String] = {
    // pathInfo starts with "/"

    // Convert file separators
    val path = pathInfo.replace('/', File.separatorChar)

    // Simplistic dumb security check
    if (path.contains(File.separator + ".") ||
        path.contains("." + File.separator) ||
        path.startsWith(".")                ||
        path.endsWith(".")) {
      None
    } else {
      // Convert to absolute path
      // user.dir: current working directory
      // See: http://www.java2s.com/Tutorial/Java/0120__Development/Javasystemproperties.htm
      val abs = System.getProperty("user.dir") + path
      Some(abs)
    }
  }
}
