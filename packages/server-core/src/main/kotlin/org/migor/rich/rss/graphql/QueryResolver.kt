package org.migor.rich.rss.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsQuery
import com.netflix.graphql.dgs.InputArgument
import com.netflix.graphql.dgs.context.DgsContext
import com.netflix.graphql.dgs.internal.DgsWebMvcRequestData
import graphql.schema.DataFetchingEnvironment
import jakarta.servlet.http.Cookie
import kotlinx.coroutines.coroutineScope
import org.apache.commons.lang3.BooleanUtils
import org.apache.commons.lang3.StringUtils
import org.migor.rich.rss.AppProfiles
import org.migor.rich.rss.api.ApiParams
import org.migor.rich.rss.api.ApiUrls
import org.migor.rich.rss.auth.CookieProvider
import org.migor.rich.rss.auth.CurrentUser
import org.migor.rich.rss.config.CacheNames
import org.migor.rich.rss.discovery.FeedDiscoveryService
import org.migor.rich.rss.generated.types.*
import org.migor.rich.rss.graphql.DtoResolver.fromDTO
import org.migor.rich.rss.graphql.DtoResolver.toDTO
import org.migor.rich.rss.graphql.DtoResolver.toPaginatonDTO
import org.migor.rich.rss.service.ArticleService
import org.migor.rich.rss.service.BucketService
import org.migor.rich.rss.service.ContentService
import org.migor.rich.rss.service.FeatureToggleService
import org.migor.rich.rss.service.FeedService
import org.migor.rich.rss.service.FilterService
import org.migor.rich.rss.service.GenericFeedService
import org.migor.rich.rss.service.ImporterService
import org.migor.rich.rss.service.PlanService
import org.migor.rich.rss.service.PropertyService
import org.migor.rich.rss.util.GenericFeedUtil
import org.migor.rich.rss.util.GenericFeedUtil.toDto
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.MimeType
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.context.request.ServletWebRequest
import java.util.*
import org.migor.rich.rss.generated.types.ApiUrls as ApiUrlsDto

@DgsComponent
class QueryResolver {

  private val log = LoggerFactory.getLogger(QueryResolver::class.simpleName)
  private val pageSize = 20

  @Autowired
  lateinit var currentUser: CurrentUser

  @Autowired
  lateinit var articleService: ArticleService

  @Autowired
  lateinit var environment: Environment

  @Autowired
  lateinit var filterService: FilterService

  @Autowired
  lateinit var cookieProvider: CookieProvider

  @Autowired
  lateinit var genericFeedService: GenericFeedService

  @Autowired
  lateinit var propertyService: PropertyService

  @Autowired
  lateinit var importerService: ImporterService

  @Autowired
  lateinit var bucketService: BucketService

  @Autowired
  lateinit var feedService: FeedService

  @Autowired
  lateinit var contentService: ContentService

  @Autowired
  lateinit var feedDiscovery: FeedDiscoveryService

  @Autowired
  lateinit var featureToggleService: FeatureToggleService

  @Autowired
  lateinit var planService: PlanService

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun bucket(
    @InputArgument data: BucketWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String
  ): Bucket = coroutineScope {
    log.info("[$corrId] bucket")
    toDTO(bucketService.findById(UUID.fromString(data.where.id)).orElseThrow())
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun buckets(
    @InputArgument data: BucketsPagedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): PagedBucketsResponse? = coroutineScope {
    log.info("[$corrId] buckets")
    val pageable = PageRequest.of(handlePage(data.page), pageSize, fromDTO(data.orderBy))
    val buckets = bucketService.findAllMatching(data.where, pageable)

    PagedBucketsResponse.newBuilder()
      .pagination(toPaginatonDTO(pageable, buckets))
      .buckets(buckets.toList().map { toDTO(it) })
      .build()
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun nativeFeeds(
    @InputArgument data: NativeFeedsPagedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): PagedNativeFeedsResponse? = coroutineScope {
    log.info("[$corrId] nativeFeeds")
    val pageable = PageRequest.of(handlePage(data.page), pageSize, fromDTO(data.orderBy))
    val feeds = if (StringUtils.isBlank(data.where.feedUrl)) {
      feedService.findAllByFilter(data.where, pageable)
    } else {
      feedService.findAllByFeedUrl(data.where.feedUrl!!, pageable)
    }
    PagedNativeFeedsResponse.newBuilder()
      .pagination(toPaginatonDTO(pageable, feeds))
      .nativeFeeds(feeds.toList().map { toDTO(it) })
      .build()
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun genericFeeds(
    @InputArgument data: GenericFeedsPagedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): PagedGenericFeedsResponse? = coroutineScope {
    log.info("[$corrId] genericFeeds")
    val pageable = PageRequest.of(handlePage(data.page), pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
    val feeds = genericFeedService.findAllByFilter(data.where, pageable)
    PagedGenericFeedsResponse.newBuilder()
      .pagination(toPaginatonDTO(pageable, feeds))
      .genericFeeds(feeds.toList().map { toDTO(it) })
      .build()
  }

  @DgsQuery
  @Cacheable(value = [CacheNames.GRAPHQL_RESPONSE], key = "'serverSettings'")
  suspend fun serverSettings(): ServerSettings = coroutineScope {
    val db = featureToggleService.withDatabase()
    val es = featureToggleService.withElasticSearch()
    val authMail = environment.acceptsProfiles(Profiles.of(AppProfiles.authMail))
    val authSSSO = environment.acceptsProfiles(Profiles.of(AppProfiles.authSSO))
    ServerSettings.newBuilder()
      .apiUrls(
        ApiUrlsDto.newBuilder()
          .webToFeed("${propertyService.apiGatewayUrl}${ApiUrls.webToFeed}")
          .build()
      )
      .features(mapOf(
        FeatureName.database to stable(db),
        FeatureName.puppeteer to stable(featureToggleService.withPuppeteer()),
        FeatureName.elasticsearch to experimental(es),
        FeatureName.genFeedFromFeed to stable(),
        FeatureName.genFeedFromPageChange to FeatureState.off,
        FeatureName.genFeedFromWebsite to stable(),
        FeatureName.authMail to stable(authMail),
        FeatureName.authSSO to stable(authSSSO),
        FeatureName.authAllowRoot to stable(!authMail, !authSSSO),
      ).map {
        feature(it.key, it.value)
      }
      ).build()
  }

  private fun feature(name: FeatureName, state: FeatureState): Feature = Feature.newBuilder()
    .name(name)
    .state(state)
    .build()


  private fun stable(vararg requirements: Boolean): FeatureState {
    return if (requirements.isNotEmpty() && requirements.all { it }) {
      FeatureState.stable
    } else {
      FeatureState.off
    }
  }

  private fun experimental(vararg requirements: Boolean): FeatureState {
    return if (requirements.isNotEmpty() && requirements.all { it }) {
      FeatureState.experimental
    } else {
      FeatureState.off
    }
  }

  @DgsQuery
//  @Cacheable(value = [CacheNames.GRAPHQL_RESPONSE], key = "'profile'", unless = "#result.isLoggedIn==true")
  @Transactional(propagation = Propagation.REQUIRED)
  suspend fun profile(dfe: DataFetchingEnvironment): Profile = coroutineScope {
    unsetSessionCookie(dfe)
    val defaultProfile = Profile.newBuilder()
      .preferReader(true)
      .preferFulltext(true)
      .isLoggedIn(false)
      .dateFormat(propertyService.dateFormat)
      .timeFormat(propertyService.timeFormat)
      .minimalFeatureState(FeatureState.experimental)
      .build()

    if (currentUser.isUser()) {
      runCatching {
        val user = currentUser.user()
        Profile.newBuilder()
          .preferReader(true)
          .preferFulltext(true)
          .dateFormat(propertyService.dateFormat)
          .timeFormat(propertyService.timeFormat)
          .isLoggedIn(true)
          .user(User.newBuilder()
            .id(user.id.toString())
            .createdAt(user.createdAt.time)
            .name(user.name)
            .acceptedTermsAndServices(user.hasApprovedTerms)
            .notificationsStreamId(user.notificationsStreamId!!.toString())
            .build())
          .minimalFeatureState(FeatureState.experimental)
          .build()
      }.getOrDefault(defaultProfile)
    } else {
      defaultProfile

    }
  }

  private fun unsetSessionCookie(dfe: DataFetchingEnvironment) {
    val cookie = cookieProvider.createExpiredSessionCookie("JSESSION")
    ((DgsContext.getRequestData(dfe)!! as DgsWebMvcRequestData).webRequest!! as ServletWebRequest).response!!.addCookie(cookie)
  }


  @DgsQuery
  @PreAuthorize("hasAuthority('READ')")
  @Transactional(propagation = Propagation.NEVER)
  suspend fun remoteNativeFeed(
    @InputArgument data: RemoteNativeFeedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): RemoteNativeFeed? = coroutineScope {
    log.info("[$corrId] remoteNativeFeed")
    val feed = feedService.parseFeedFromUrl(corrId, data.nativeFeedUrl)
    RemoteNativeFeed.newBuilder()
      .description(feed.description)
      .title(feed.title)
//      Author=feed.author,
      .feedUrl(feed.feedUrl)
      .websiteUrl(feed.websiteUrl)
      .language(feed.language)
      .publishedAt(feed.publishedAt.time)
      .expired(BooleanUtils.isTrue(feed.expired))
      .items(feed.items.map {
        FilteredContent.newBuilder()
          .omitted(data.applyFilter?.let { filter -> !filterService.matches(corrId, it, filter.filter) }
            ?: false)
          .content(Content.newBuilder()
            .title(it.title)
            .description(it.contentText)
            .contentText(it.contentText)
            .contentRaw(it.contentRaw)
            .contentRawMime(it.contentRawMime)
            .publishedAt(it.publishedAt.time)
            .startingAt(it.startingAt?.time)
            .url(it.url)
            .imageUrl(it.imageUrl)
            .build()
          )
          .build()
      })
      .build()
  }

  @DgsQuery
  @PreAuthorize("hasAuthority('READ')")
  @Transactional(propagation = Propagation.NEVER)
  suspend fun discoverFeeds(
    @InputArgument data: DiscoverFeedsInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): FeedDiscoveryResponse = coroutineScope {
    log.info("[$corrId] discoverFeeds")
    val fetchOptions = GenericFeedUtil.fromDto(data.fetchOptions)
    val discovery = feedDiscovery.discoverFeeds(corrId, fetchOptions)
    val response = discovery.results

    val document = response.document
    FeedDiscoveryResponse.newBuilder()
      .failed(response.failed)
      .errorMessage(response.errorMessage)
      .document(FeedDiscoveryDocument.newBuilder()
          .mimeType(document.mimeType)
          .htmlBody(document.mimeType?.let {
            if (MimeType.valueOf(it).subtype == "html") {
              document.body
            } else {
              null
            }
          }
          )
          .title(document.title)
          .url(document.url)
          .language(document.language)
          .description(document.description)
          .imageUrl(document.imageUrl)
          .build())
      .websiteUrl(discovery.options.harvestUrl)
      .nativeFeeds(response.nativeFeeds.map {toDto(it)
      })
      .fetchOptions(toDto(data.fetchOptions))
      .genericFeeds(
        GenericFeeds.newBuilder()
          .parserOptions(toDto(data.parserOptions))
          .feeds(response.genericFeedRules.map {toDto(it) })
          .build()
      )
      .build()
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun article(
    @InputArgument data: ArticleWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): Article = coroutineScope {
    log.info("[$corrId] article")
    toDTO(articleService.findById(UUID.fromString(data.where.id)).orElseThrow())
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun content(
    @InputArgument data: ContentWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): Content = coroutineScope {
    log.info("[$corrId] content")
    toDTO(contentService.findById(UUID.fromString(data.where.id)).orElseThrow())
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun nativeFeed(
    @InputArgument data: NativeFeedWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): NativeFeed = coroutineScope {
    log.info("[$corrId] nativeFeed")
    toDTO(feedService.findNativeById(UUID.fromString(data.where.id)).orElseThrow())
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun genericFeed(
    @InputArgument data: GenericFeedWhereInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): GenericFeed? = coroutineScope {
    log.info("[$corrId] genericFeed")
    toDTO(genericFeedService.findById(UUID.fromString(data.where.id)).orElseThrow())
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun importers(
    @InputArgument data: ImportersPagedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): PagedImportersResponse = coroutineScope {
    log.info("[$corrId] importers")
    val pageable = PageRequest.of(handlePage(data.page), pageSize, fromDTO(data.orderBy))
    val items = importerService.findAllByFilter(data.where, pageable).map { toDTO(it) }
    PagedImportersResponse.newBuilder()
      .pagination(toPaginatonDTO(pageable, items))
      .importers(items)
      .build()
  }

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun articles(
    @InputArgument data: ArticlesPagedInput,
    @RequestHeader(ApiParams.corrId) corrId: String,
  ): PagedArticlesResponse = coroutineScope {
    log.info("[$corrId] articles")
    val pageable = PageRequest.of(handlePage(data.page), pageSize, fromDTO(data.orderBy))
    val items = articleService.findAllByFilter(data.where, pageable)
    PagedArticlesResponse.newBuilder()
      .pagination(toPaginatonDTO(pageable, items))
      .articles(items.map { toDTO(it) })
      .build()
  }

  private fun handlePage(page: Int?): Int =
    page ?: 0

  @DgsQuery
  @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
  suspend fun plans(@RequestHeader(ApiParams.corrId) corrId: String,): List<Plan> = coroutineScope {
    log.info("[$corrId] plans")
    planService.findAll().map { Plan.newBuilder()
      .id(it.id.toString())
      .name(toDTO(it.name))
      .costs(it.costs)
      .availability(toDTO(it.availability))
      .isPrimary(it.primary)
      .build()
    }
  }
}
