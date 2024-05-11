package org.migor.feedless.api.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import kotlinx.coroutines.coroutineScope
import org.migor.feedless.AppProfiles
import org.migor.feedless.common.PropertyService
import org.migor.feedless.config.CacheNames
import org.migor.feedless.data.jpa.enums.fromDto
import org.migor.feedless.generated.types.ProfileName
import org.migor.feedless.generated.types.ServerSettings
import org.migor.feedless.generated.types.ServerSettingsContextInput
import org.migor.feedless.license.LicenseService
import org.migor.feedless.plan.FeatureService
import org.migor.feedless.plan.ProductService
import org.migor.feedless.plan.toDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.env.Environment

@DgsComponent
@org.springframework.context.annotation.Profile(AppProfiles.database)
class ServerSettingsResolver {

  private val log = LoggerFactory.getLogger(ServerSettingsResolver::class.simpleName)

  @Autowired
  lateinit var environment: Environment

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var featureService: FeatureService

  @Value("\${APP_VERSION}")
  lateinit var version: String

  @Autowired
  lateinit var productService: ProductService

  @Autowired
  lateinit var licenseService: LicenseService

  @DgsQuery
  @Cacheable(
    value = [CacheNames.GRAPHQL_RESPONSE],
    keyGenerator = "cacheKeyGenerator"
  ) // https://stackoverflow.com/questions/14072380/cacheable-key-on-multiple-method-arguments
  suspend fun serverSettings(
    @InputArgument data: ServerSettingsContextInput,
  ): ServerSettings = coroutineScope {
    log.info("serverSettings $data")
    val product = data.product.fromDto()

    ServerSettings.newBuilder()
      .appUrl(productService.getAppUrl(product))
      .version(version)
      .buildFrom(licenseService.buildFrom())
      .profiles(environment.activeProfiles.map {
        when (it) {
          AppProfiles.authMail -> ProfileName.authMail
          AppProfiles.authSSO -> ProfileName.authSSO
          AppProfiles.selfHosted -> ProfileName.selfHosted
          else -> null
        }
      }.filterNotNull())
      .gatewayUrl(productService.getGatewayUrl(product))
      .features(featureService.findAllByProduct(product).map { it.toDto() })
      .build()
  }
}

