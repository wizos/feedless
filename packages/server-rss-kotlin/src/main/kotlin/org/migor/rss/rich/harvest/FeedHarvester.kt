package org.migor.rss.rich.harvest

import com.rometools.rome.feed.module.DCModule
import com.rometools.rome.feed.synd.SyndContent
import com.rometools.rome.feed.synd.SyndEntry
import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.migor.rss.rich.database.enums.FeedStatus
import org.migor.rss.rich.database.model.Article
import org.migor.rss.rich.database.model.Feed
import org.migor.rss.rich.database.model.NamespacedTag
import org.migor.rss.rich.database.model.TagNamespace
import org.migor.rss.rich.database.repository.ArticleRepository
import org.migor.rss.rich.database.repository.FeedRepository
import org.migor.rss.rich.harvest.feedparser.FeedContextResolver
import org.migor.rss.rich.harvest.feedparser.NativeFeedResolver
import org.migor.rss.rich.service.ArticleService
import org.migor.rss.rich.service.ExporterTargetService
import org.migor.rss.rich.service.FeedService
import org.migor.rss.rich.service.HttpService
import org.migor.rss.rich.util.HtmlUtil
import org.migor.rss.rich.util.JsonUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.util.MimeType
import java.util.*
import java.util.stream.Collectors
import javax.annotation.PostConstruct

@Service
class FeedHarvester internal constructor() {

  private val log = LoggerFactory.getLogger(FeedHarvester::class.simpleName)

  @Autowired
  lateinit var articleService: ArticleService

  @Autowired
  lateinit var exporterTargetService: ExporterTargetService

  @Autowired
  lateinit var articleRepository: ArticleRepository

  @Autowired
  lateinit var feedRepository: FeedRepository

  @Autowired
  lateinit var feedService: FeedService

  @Autowired
  lateinit var httpService: HttpService

  private lateinit var feedContextResolvers: Array<FeedContextResolver>

  @PostConstruct
  fun onInit() {
    feedContextResolvers = arrayOf(
      NativeFeedResolver()
    )

    feedContextResolvers.sortByDescending { feedUrlResolver -> feedUrlResolver.priority() }
    log.info(
      "Using feedUrlResolvers ${
        feedContextResolvers.map { feedUrlResolver -> "$feedUrlResolver priority: ${feedUrlResolver.priority()}" }
          .joinToString(", ")
      }"
    )
  }

  fun harvestFeed(corrId: String, feed: Feed) {
    try {
      this.log.info("[$corrId] Harvesting feed ${feed.id} (${feed.feedUrl})")
      val feedData = fetchFeed(corrId, feed).map { response -> feedService.parseFeed(corrId, response) }
      if (feedData.isEmpty()) {
        throw RuntimeException("[$corrId] No feeds extracted")
      } else {
        handleFeedData(corrId, feed, feedData)
      }

      if (FeedStatus.ok != feed.status) {
        this.log.debug("[$corrId] status-change for Feed ${feed.feedUrl}: ${feed.status} -> ok")
        feedService.redeemStatus(feed)
      }
    } catch (ex: Exception) {
      log.error("[$corrId] Harvest failed ${ex.message}")
      feedService.updateNextHarvestDateAfterError(corrId, feed, ex)
    } finally {
      this.log.debug("[$corrId] Finished feed ${feed.feedUrl}")
    }
  }

  private fun updateFeedDetails(corrId: String, feedData: FeedData, feed: Feed) {
    feed.description = StringUtils.trimToNull(feedData.feed.description)
    feed.title = feedData.feed.title
    feed.tags =
      feedData.feed.categories.map { syndCategory -> NamespacedTag(TagNamespace.NONE, syndCategory.name) }.toList()
    feed.lang = lang(feedData.feed.language)
    val dcModule = feedData.feed.getModule("http://purl.org/dc/elements/1.1/") as DCModule?
    if (dcModule != null && feed.lang == null) {
      feed.lang = lang(dcModule.language)
    }
    feed.homePageUrl = feedData.feed.link
    var ftData = arrayOf(feed.title, feed.description, feed.feedUrl, feed.homePageUrl)

    if (!feed.homePageUrl.isNullOrEmpty() && feed.status === FeedStatus.unresolved) {
      try {
        val response = httpService.httpGet(corrId, feed.homePageUrl!!, 200)
        val doc = Jsoup.parse(response.responseBody)
        ftData = ftData.plus(doc.title())
      } catch (e: HarvestException) {
        // ignore
      }
    }
    feed.fulltext = ftData
      .filter { value -> StringUtils.isNotBlank(value) }.joinToString(" ")

    feedService.update(feed)
  }

  private fun lang(language: String?): String? {
    val lang = StringUtils.trimToNull(language)
    return if (lang == null || lang.length < 2) {
      null
    } else {
      lang.substring(0, 2)
    }
  }

  private fun handleFeedData(corrId: String, feed: Feed, feedData: List<FeedData>) {
    if (feedData.isNotEmpty()) {
      updateFeedDetails(corrId, feedData.first(), feed)

      val articles = feedData.first().feed.entries
        .asSequence()
        .filterNotNull()
        .map { syndEntry -> createArticle(syndEntry, feed) }
        .filterNotNull()
        .filter { article -> !existsArticleByUrl(article.url!!) }
        .map { article -> enrichArticle(corrId, article, feed) }
        .toList()

      val newArticlesCount = articles.stream().filter { pair: Pair<Boolean, Article>? -> pair!!.first }.count()
      if (newArticlesCount > 0) {
        log.info("[$corrId] Updating $newArticlesCount articles for ${feed.feedUrl}")
        feedService.updateUpdatedAt(corrId, feed)
      } else {
        log.debug("[$corrId] Up-to-date ${feed.feedUrl}")
      }

      articles.map { pair: Pair<Boolean, Article> -> pair.second }
        .forEach { article: Article ->
          runCatching {
            exporterTargetService.pushArticleToTargets(
              corrId,
              article,
              feed.streamId!!,
              "system",
              article.pubDate,
              emptyList(),
            )
          }.onFailure { log.error(it.message) }
        }
      feedService.updateNextHarvestDate(corrId, feed, newArticlesCount > 0)
      if (newArticlesCount > 0) {
        this.feedRepository.setLastUpdatedAt(feed.id!!, Date())
      }
    } else {
      feedService.updateNextHarvestDate(corrId, feed, false)
    }
  }

  private fun existsArticleByUrl(url: String): Boolean {
    return articleRepository.existsByUrl(url)
  }

  private fun enrichArticle(corrId: String, article: Article, feed: Feed): Pair<Boolean, Article> {
    val optionalEntry = articleRepository.findByUrl(article.url!!)
    return if (optionalEntry.isPresent) {
      val (updatedArticle, _) = updateArticleProperties(optionalEntry.get(), article)
      this.articleService.triggerContentEnrichment(corrId, updatedArticle, feed)
      Pair(false, updatedArticle)
    } else {
      this.articleService.triggerContentEnrichment(corrId, article, feed)
      Pair(true, article)
    }
  }

  private fun updateArticleProperties(existingArticle: Article, newArticle: Article): Pair<Article, Boolean> {
    val changedTitle = existingArticle.title.equals(newArticle.title)
    if (changedTitle) {
      existingArticle.title = newArticle.title
    }
    val changedContent = existingArticle.contentRaw == newArticle.contentRaw
    if (changedContent) {
      existingArticle.contentRaw = newArticle.contentRaw
    }
    val changedContentHtml = existingArticle.contentText.equals(newArticle.contentText)
    if (changedContentHtml) {
      existingArticle.contentText = newArticle.contentText
    }

    val allTags = HashSet<NamespacedTag>()
    newArticle.tags?.let { tags -> allTags.addAll(tags) }
    existingArticle.tags?.let { tags -> allTags.addAll(tags) }
    existingArticle.tags = allTags.toList()
    return Pair(existingArticle, changedTitle || changedContent || changedContentHtml)
  }

  private fun createArticle(syndEntry: SyndEntry, feed: Feed): Article? {
    return try {
      val article = Article()
      article.url = syndEntry.link
      article.title = syndEntry.title
      val (text, html) = extractContent(syndEntry)
      text?.let { t ->
        run {
          article.contentRaw = t.second
          article.contentRawMime = t.first.toString()
        }
      }

      html?.let { t ->
        run {
          article.contentText = HtmlUtil.html2text(t.second)
        }
      }

      article.author = getAuthor(syndEntry)
      val tags = syndEntry.categories
        .map { syndCategory -> NamespacedTag(TagNamespace.NONE, syndCategory.name) }
        .toMutableSet()
      if (syndEntry.enclosures != null && syndEntry.enclosures.isNotEmpty()) {
        tags.addAll(
          syndEntry.enclosures
            .filterNotNull()
            .map { enclusure ->
              NamespacedTag(
                TagNamespace.CONTENT,
                MimeType(enclusure.type).type.lowercase(Locale.getDefault())
              )
            }
        )
      }
      article.enclosures = JsonUtil.gson.toJson(syndEntry.enclosures)
//      article.putDynamicField("", "enclosures", syndEntry.enclosures)
      article.tags = tags.toList()
      article.commentsFeedUrl = syndEntry.comments
      article.sourceUrl = feed.feedUrl
      article.released = !feed.harvestSite

      article.pubDate = Optional.ofNullable(syndEntry.publishedDate).orElse(Date())
      article.createdAt = Date()
      article
    } catch (e: Exception) {
      null
    }
  }

  private fun getAuthor(syndEntry: SyndEntry) =
    Optional.ofNullable(StringUtils.trimToNull(syndEntry.author)).orElse("unknown")

  private fun extractContent(syndEntry: SyndEntry): Pair<Pair<MimeType, String>?, Pair<MimeType, String>?> {
    val contents = ArrayList<SyndContent>()
    contents.addAll(syndEntry.contents)
    if (syndEntry.description != null) {
      contents.add(syndEntry.description)
    }
    val html = contents.find { syndContent ->
      syndContent.type != null && syndContent.type.lowercase(Locale.getDefault()).endsWith("html")
    }?.let { htmlContent -> Pair(MimeType.valueOf("text/html"), htmlContent.value) }
    val text = if (contents.isNotEmpty()) {
      if (html == null) {
        Pair(MimeType.valueOf("text/plain"), contents.first().value)
      } else {
        Pair(MimeType.valueOf("text/plain"), HtmlUtil.html2text(html.second))
      }
    } else {
      null
    }
    return Pair(text, html)
  }

  private fun fetchFeed(corrId: String, feed: Feed): List<HarvestResponse> {
    val feedContextResolver = findFeedContextResolver(feed)
    return Optional.ofNullable(feedContextResolver)
      .orElseThrow { throw RuntimeException("No feedContextResolver found for feed ${feed.feedUrl}") }
      .getHarvestContexts(feed)
      .stream()
      .map { context -> fetchFeedUrl(corrId, context) }
      .collect(Collectors.toList())
  }

  private fun findFeedContextResolver(feed: Feed): FeedContextResolver {
    return feedContextResolvers.first { feedResolver -> feedResolver.canHarvest(feed) }
  }

  private fun fetchFeedUrl(corrId: String, context: HarvestContext): HarvestResponse {
    val request = httpService.prepareGet(context.feedUrl)
    if (context.prepareRequest != null) {
      log.info("[$corrId] Preparing request")
      context.prepareRequest.invoke(request)
    }
    log.info("[$corrId] GET ${context.feedUrl}")
    val response = httpService.executeRequest(corrId, request, context.expectedStatusCode)
    return HarvestResponse(context.feedUrl, response)
  }
}
