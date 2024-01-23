package org.migor.feedless.service

import org.apache.commons.lang3.StringUtils
import org.migor.feedless.AppProfiles
import org.migor.feedless.api.auth.CurrentUser
import org.migor.feedless.api.dto.RichArticle
import org.migor.feedless.api.dto.RichFeed
import org.migor.feedless.api.graphql.DtoResolver.fromDto
import org.migor.feedless.config.CacheNames
import org.migor.feedless.data.jpa.StandardJpaFields
import org.migor.feedless.data.jpa.enums.EntityVisibility
import org.migor.feedless.data.jpa.enums.ReleaseStatus
import org.migor.feedless.data.jpa.models.PluginRef
import org.migor.feedless.data.jpa.models.ScrapeSourceEntity
import org.migor.feedless.data.jpa.models.SourceSubscriptionEntity
import org.migor.feedless.data.jpa.models.WebDocumentEntity
import org.migor.feedless.data.jpa.models.toDto
import org.migor.feedless.data.jpa.repositories.ScrapeSourceDAO
import org.migor.feedless.data.jpa.repositories.SourceSubscriptionDAO
import org.migor.feedless.feed.parser.json.JsonAttachment
import org.migor.feedless.generated.types.PluginExecutionInput
import org.migor.feedless.generated.types.ScrapeRequestInput
import org.migor.feedless.generated.types.SourceSubscription
import org.migor.feedless.generated.types.SourceSubscriptionCreateInput
import org.migor.feedless.generated.types.SourceSubscriptionsCreateInput
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Profile(AppProfiles.database)
class SourceSubscriptionService {

  private val log = LoggerFactory.getLogger(SourceSubscriptionService::class.simpleName)

  @Autowired
  lateinit var scrapeSourceDAO: ScrapeSourceDAO

  @Autowired
  lateinit var sourceSubscriptionDAO: SourceSubscriptionDAO

  @Autowired
  lateinit var currentUser: CurrentUser

  @Autowired
  lateinit var planConstraints: PlanConstraintsService

  @Autowired
  lateinit var webDocumentService: WebDocumentService

  @Autowired
  lateinit var propertyService: PropertyService

  @Transactional
  fun create(corrId: String, data: SourceSubscriptionsCreateInput): List<SourceSubscription> {
    log.info("[$corrId] create sub")
    return data.subscriptions.map { createSubscription(corrId, it).toDto() }
  }

  private fun createSubscription(corrId: String, subInput: SourceSubscriptionCreateInput): SourceSubscriptionEntity {
    val sub = SourceSubscriptionEntity()

    sub.title = subInput.sinkOptions.title
    sub.description = subInput.sinkOptions.description
    sub.visibility = planConstraints.coerceVisibility(fromDto(subInput.sinkOptions.visibility))
    sub.sources = subInput.sources.map { createScrapeSource(corrId, it, sub) }.toMutableList()
    val ownerId = currentUser.user().id
    sub.ownerId = ownerId
    sub.plugins = subInput.sinkOptions.plugins.map { it.toPluginRef() }
    sub.schedulerExpression = planConstraints.auditRefreshCron(subInput.sourceOptions.refreshCron)
    sub.retentionMaxItems = planConstraints.coerceRetentionMaxItems(subInput.sinkOptions.retention.maxItems, ownerId)
    sub.retentionMaxAgeDays = planConstraints.coerceRetentionMaxAgeDays(subInput.sinkOptions.retention.maxAgeDays)
    sub.disabledFrom = planConstraints.coerceScrapeSourceExpiry(corrId, ownerId)

    return sourceSubscriptionDAO.save(sub)
  }

  private fun createScrapeSource(corrId: String, req: ScrapeRequestInput, sub: SourceSubscriptionEntity): ScrapeSourceEntity {
    val entity = ScrapeSourceEntity()
    val scrapeRequest = req.fromDto()
    log.info("[$corrId] create source ${scrapeRequest.page.url}")
    planConstraints.coerceScrapeRequestMaxActions(scrapeRequest.page.actions?.size)
    planConstraints.coerceScrapeRequestTimeout(scrapeRequest.page.timeout)
    entity.scrapeRequest = scrapeRequest
    entity.subscriptionId = sub.id
    return scrapeSourceDAO.save(entity)
  }

  @Cacheable(value = [CacheNames.FEED_RESPONSE], key = "\"bucket/\" + #subscriptionId")
  @Transactional(readOnly = true)
  fun getFeedBySubscriptionId(subscriptionId: String, page: Int): RichFeed {
    val id = UUID.fromString(subscriptionId)
    val subscription = sourceSubscriptionDAO.findById(id).orElseThrow {IllegalArgumentException("subscription not found")}
    val items = webDocumentService.findAllBySubscriptionId(id, page, ReleaseStatus.released)
      .map { it.toRichArticle() }

    val richFeed = RichFeed()
    richFeed.id = "subscription:${subscriptionId}"
    richFeed.title = subscription.title
    richFeed.description = subscription.description
    richFeed.websiteUrl = "${propertyService.appHost}/feed/$subscriptionId"
    richFeed.publishedAt = items.maxOfOrNull { it.publishedAt } ?: Date()
    richFeed.items = items
    richFeed.imageUrl = null
    richFeed.expired = false
    richFeed.selfPage = page
    richFeed.feedUrl = "${propertyService.apiGatewayUrl}/feed/${subscriptionId}/atom"

    return richFeed
  }

  fun findAll(offset: Int, pageSize: Int, userId: UUID?): List<SourceSubscriptionEntity> {
    val pageable = PageRequest.of(offset, pageSize.coerceAtMost(10), Sort.by(Sort.Direction.DESC, StandardJpaFields.createdAt))
    return (userId
      ?.let { sourceSubscriptionDAO.findAllByOwnerId(it, pageable) }
      ?: emptyList())
  }

  fun findById(id: String): SourceSubscriptionEntity {
    val sub = sourceSubscriptionDAO.findById(UUID.fromString(id)).orElseThrow { RuntimeException("not found") }
    return if (sub.visibility === EntityVisibility.isPublic) {
      sub
    } else {
      if (sub.ownerId == currentUser.userId()) {
        sub
      } else {
        throw RuntimeException("unauthorized")
      }
    }
  }

  fun delete(corrId: String, id: UUID) {
    sourceSubscriptionDAO.deleteByIdAndOwnerId(id, currentUser.user().id)
  }
}

private fun PluginExecutionInput.toPluginRef(): PluginRef {
  return PluginRef(id = this.pluginId, params = this.params)
}

private fun WebDocumentEntity.toRichArticle(): RichArticle {
  val article = RichArticle()
  article.id = this.id.toString()
  article.title = StringUtils.trimToEmpty(this.contentTitle)
  article.url = this.url
  article.attachments = this.attachments.map {
        val a = JsonAttachment()
        a.url = it.url
        a.type = it.type
//                a.size = it.size
//        a.duration = it.duration
        a
  }
  article.contentText = StringUtils.trimToEmpty(this.contentText)
  article.contentRaw = this.contentRaw?.let { Base64.getEncoder().encodeToString(this.contentRaw) }
  article.contentRawMime = this.contentRawMime
  article.publishedAt = this.releasedAt
  article.startingAt = this.startingAt
  article.imageUrl = this.imageUrl
  return article

}
