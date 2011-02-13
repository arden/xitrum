package xt.vc.env

import java.util.{Map => JMap, LinkedHashMap => JLinkedHashMap, List => JList}

import org.jboss.netty.handler.codec.http.{DefaultHttpResponse, HttpResponseStatus, HttpVersion, HttpHeaders}
import HttpResponseStatus._
import HttpVersion._

import xt.{Action, Config}

trait ExtEnv extends Env {
  this: Action =>

  lazy val allParams = {
    val ret = new JLinkedHashMap[String, JList[String]]()
    // The order is important because we want the later to overwrite the former
    ret.putAll(uriParams)
    ret.putAll(bodyParams)
    ret.putAll(pathParams)
    ret
  }

  /** The default response is empty 200 OK */
  lazy val response = {
    val ret = new DefaultHttpResponse(HTTP_1_1, OK)
    HttpHeaders.setContentLength(ret, 0)
    ret
  }

  // Avoid encoding, decoding when cookies/session is not touched by the application
  private var _cookiesTouched = false
  private var _sessionTouched = false

  def isCookiesTouched = _cookiesTouched
  def isSessionTouched = _sessionTouched

  lazy val cookies = {
    _cookiesTouched = true
    new Cookies(request)
  }

  lazy val session = {
    _sessionTouched = true
    Config.sessionStore.restore(this)
  }

  lazy val at = new At
}