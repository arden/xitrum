package xitrum.handler

import java.text.SimpleDateFormat
import java.util.{Locale, TimeZone}

object NotModified {
  val TTL_IN_MINUTES = 10

  // SimpleDateFormat is locale dependent
  // Avoid the case when Xitrum is run on for example Japanese platform
  private val rfc2822 = {
    val ret = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
    ret.setTimeZone(TimeZone.getTimeZone("GMT"))
    ret
  }

  def formatRfc2822(timestamp: Long) = rfc2822.format(timestamp)
}
