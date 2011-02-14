package xitrum.routing

import java.lang.reflect.Method
import java.util.{LinkedHashMap => JLinkedHashMap, List => JList}
import scala.collection.mutable.{ArrayBuffer, StringBuilder}
import org.jboss.netty.handler.codec.http.{HttpMethod, QueryStringEncoder}

import xitrum._
import xitrum.vc.env.{Env, PathInfo}

object Routes extends Logger {
  type Pattern         = String
  type CompiledPattern = Array[(String, Boolean)]  // String: token, Boolean: true if the token is constant
  type Route           = (HttpMethod, Pattern,         Class[Action])
  type CompiledRoute   = (HttpMethod, CompiledPattern, Class[Action])

  private var compiledRoutes: Iterable[CompiledRoute] = _

  def collectAndCompile {
    // Avoid loading twice in some servlet containers
    if (compiledRoutes != null) return

    val routes = (new RouteCollector).collect

    // Compile and log routes at the same time because the compiled routes do
    // not contain the original URL pattern.

    val patternMaxLength = routes.foldLeft(0) { (max, r) =>
      val len = r._2.length
      if (max < len) len else max
    }

    val builder = new StringBuilder
    builder.append("Routes:\n")
    compiledRoutes = routes.map { r =>
      val method      = r._1
      val pattern     = r._2
      val actionClass = r._3

      val format = if (actionClass.newInstance.isInstanceOf[Postback])
        "%-6s %-" + patternMaxLength + "s %s (POSTBACK)\n"
      else
        "%-6s %-" + patternMaxLength + "s %s\n"
      builder.append(format.format(method, pattern, actionClass.getName))

      compileRoute(r)
    }
    logger.info(builder.toString)
  }

  /**
   * @return None if not matched or Some(pathParams)
   *
   * controller name and action name are put int pathParams.
   */
  def matchRoute(method: HttpMethod, pathInfo: PathInfo): Option[(Class[Action], Env.Params)] = {
    val tokens = pathInfo.tokens
    val max1   = tokens.size

    var pathParams: Env.Params = null

    def finder(cr: CompiledRoute): Boolean = {
      val (om, compiledPattern, _action) = cr

      // Check method
      if (method.getName != "POSTBACK" && om != method) return false

      val max2 = compiledPattern.size

      // Check the number of tokens
      // max2 must be <= max1
      // If max2 < max1, the last token must be "*" and non-fixed

      if (max2 > max1) return false

      if (max2 < max1) {
        if (max2 == 0) return false

        val lastToken = compiledPattern.last
        if (lastToken._2) return false
      }

      // Special case
      // 0 = max2 <= max1
      if (max2 == 0) {
        if (max1 == 0) {
          pathParams = new JLinkedHashMap[String, JList[String]]()
          return true
        }

        return false
      }

      // 0 < max2 <= max1

      pathParams = new JLinkedHashMap[String, JList[String]]()
      var i = 0  // i will go from 0 until max1

      compiledPattern.forall { tc =>
        val (token, fixed) = tc

        val ret = if (fixed)
          (token == tokens(i))
        else {
          if (i == max2 - 1) {  // The last token
            if (token == "*") {
              val value = tokens.slice(i, max1).mkString("/")
              pathParams.put(token, Util.toValues(value))
              true
            } else {
              if (max2 < max1) {
                false
              } else {  // max2 = max1
                pathParams.put(token, Util.toValues(tokens(i)))
                true
              }
            }
          } else {
            if (token == "*") {
              false
            } else {
              pathParams.put(token, Util.toValues(tokens(i)))
              true
            }
          }
        }

        i += 1
        ret
      }
    }

    compiledRoutes.find(finder) match {
      case Some(cr) =>
        val (m, compiledPattern, action) = cr
        pathParams.put("action", Util.toValues(action.getName))
        Some((action, pathParams))

      case None => None
    }
  }

  def urlFor(actionClass: Class[Action], params: (String, Any)*): String = {
    val cpo = compiledRoutes.find { case (_, _, klass) => klass == actionClass }
    if (cpo.isEmpty) throw new Exception("No route for " + actionClass)

    val compiledPattern = cpo.get._2

    var map = params.toMap
    val tokens = compiledPattern.map { case (token, constant) =>
      if (constant) {
        token
      } else {
        val ret = map(token)
        map = map - token
        ret
      }
    }
    val url = "/" + tokens.mkString("/")

    val qse = new QueryStringEncoder(url, Config.paramCharset)
    for ((k, v) <- map) qse.addParam(k, v.toString)
    qse.toString
  }

  //----------------------------------------------------------------------------

  private def compileRoute(route: Route): CompiledRoute = {
    val (method, pattern, action) = route
    val cp = compilePattern(pattern)
    (method, cp, action)
  }

  private def compilePattern(pattern: String): CompiledPattern = {
    val tokens = pattern.split("/").filter(_ != "")
    tokens.map { e: String =>
      val constant = !e.startsWith(":")
      val token    = if (constant) e else e.substring(1)
      (token, constant)
    }
  }
}