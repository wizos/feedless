package org.migor.rss.rich.pipeline

import org.apache.commons.lang3.StringUtils
import org.jsoup.Jsoup
import org.migor.rss.rich.database.enums.ArticleHookType
import org.migor.rss.rich.database.model.Article
import org.migor.rss.rich.database.model.ArticleHookSpec
import org.migor.rss.rich.database.model.Bucket
import org.migor.rss.rich.database.model.NamespacedTag
import org.migor.rss.rich.database.repository.NoFollowUrlRepository
import org.migor.rss.rich.harvest.ArticleSnapshot
import org.migor.rss.rich.service.ArticleService
import org.migor.rss.rich.service.FeedService.Companion.absUrl
import org.migor.rss.rich.service.GraphService
import org.migor.rss.rich.service.PipelineService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.net.URL
import java.util.*

@Service
class FollowLinksHook : ArticleHook {
  private val log = LoggerFactory.getLogger(PipelineService::class.simpleName)

  @Autowired
  lateinit var articleService: ArticleService

  @Autowired
  lateinit var graphService: GraphService

  @Autowired
  lateinit var noFollowUrlRepository: NoFollowUrlRepository

  override fun process(
    corrId: String,
    snapshot: ArticleSnapshot,
    bucket: Bucket,
    hookSpec: ArticleHookSpec,
    addTag: (NamespacedTag) -> Boolean,
    addData: (Pair<String, String>) -> String?
  ): Boolean {

    return true
  }

  override fun type(): ArticleHookType = ArticleHookType.followLinks

  val blacklist = listOf(
    "paypal.me", "apple.com", "twitter.com", "patreon.com", "google.com", "amazon.com",
    "paypal.me", "facebook.com", "instagram.com", "tiktok.com"
  )

  private fun followLinks(article: Article, bucket: Bucket) {
    if (article.hasReadability == true) {
      Optional.ofNullable(article.getContentOfMime("text/html"))
        .ifPresentOrElse({ content ->
          run {
            val doc = Jsoup.parse(content)
            val urls = doc.body().select("a[href]")
              .map { link -> absUrl(article.url!!, link.attr("href")) }
              .distinct()
              .filter { url -> StringUtils.isNotBlank(url) }
              .filter { url -> isQualifiedUrl(url) }

            urls.forEach { url -> graphService.link(article.url!!, url) }

            val groups = urls.groupBy { url -> URL(url).host }
            val firstUrlPerDomain = groups.keys.map { domain -> groups[domain]!!.first() }

            val toSeedFromUrls = Stack<String>()
            toSeedFromUrls.addAll(firstUrlPerDomain)
            toSeedFromUrls.shuffled()

            var seeded = 0
            while (seeded < 1 && toSeedFromUrls.isNotEmpty()) {
              val url = toSeedFromUrls.pop()
              val success = articleService.tryCreateArticleFromContainedUrlForBucket(url, article.url!!, bucket)
              if (success) {
                log.info("Seeded article from $url to bucket ${bucket.id}")
                seeded++
              }
            }
          }
        }, { log.error("Readability is null but hasReadability=true for ${article.url}") })
    }
  }

  private fun isQualifiedUrl(url: String): Boolean {
    try {
      val parsed = URL(url)
      if (StringUtils.isBlank(parsed.path.replace("/", ""))) {
        return false
      }

      return blacklist.none { blacklistedUrl -> url.contains(blacklistedUrl) } && !noFollowUrlRepository.existsByUrlStartingWith(
        url
      )
    } catch (e: Exception) {
      return false
    }
  }


}