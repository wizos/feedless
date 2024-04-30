package org.migor.feedless.session

import jakarta.servlet.http.Cookie
import org.migor.feedless.AppProfiles
import org.migor.feedless.common.PropertyService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Service


@Service
@Profile(AppProfiles.api)
class CookieProvider {
  private val log = LoggerFactory.getLogger(CookieProvider::class.simpleName)

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var tokenProvider: TokenProvider

  fun createTokenCookie(corrId: String, authToken: Jwt): Cookie {
    log.info("[$corrId] creating token cookie")
    val cookie = Cookie("TOKEN", authToken.tokenValue)
    cookie.isHttpOnly = true
    cookie.domain = propertyService.domain
    cookie.maxAge = tokenProvider.getUserTokenExpiration().seconds.toInt()
    cookie.secure = true
    cookie.path = "/"
    return cookie
  }

  fun createExpiredSessionCookie(name: String): Cookie {
    val cookie = Cookie(name, "")
    cookie.isHttpOnly = true
    cookie.domain = propertyService.domain
    cookie.maxAge = 0
    return cookie
  }
}
