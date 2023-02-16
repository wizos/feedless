package org.migor.rich.rss.service

import org.migor.rich.rss.AppProfiles
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service

@Service
class FeatureToggleService {
  @Autowired
  lateinit var puppeteerService: PuppeteerService

  @Autowired
  lateinit var environment: Environment

  fun withAuthentication(): Boolean {
    return false
  }

  fun withDatabase(): Boolean {
    return environment.acceptsProfiles(Profiles.of(AppProfiles.database))
  }

  fun withPuppeteer(): Boolean {
    return puppeteerService.canPrerender()
  }

  fun withElasticSearch(): Boolean {
    return environment.acceptsProfiles(Profiles.of(AppProfiles.elasticsearch))
  }

}