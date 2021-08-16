package org.migor.rss.rich.discovery

import org.asynchttpclient.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.migor.rss.rich.harvest.feedparser.FeedType
import org.migor.rss.rich.service.FeedService
import org.migor.rss.rich.service.FeedService.Companion.absUrl
import org.migor.rss.rich.util.FeedUtil
import org.springframework.stereotype.Service
import java.net.URL


@Service
class NativeFeedLocator {

  fun locate(response: Response, url: String): List<FeedReference> {
    val feedType = FeedUtil.detectFeedTypeForResponse(response)
    return if (feedType == FeedType.NONE) {
      val document = Jsoup.parse(response.responseBody)
      //    <link rel="alternate" type="application/rss+xml" title="yellowchicken &raquo; Feed" href="https://yellowchicken.wordpress.com/feed/" />

      document.select("link[rel=alternate][title][type]").mapNotNull { element -> toFeedReference(element, url) }
    } else {
      listOf(FeedReference(url = url, type = feedType, title = "Feed"))
    }
  }

  private fun toFeedReference(element: Element, url: String): FeedReference? {
    try {
      return FeedReference(
        absUrl(url, element.attr("href")),
        FeedUtil.detectFeedType(element.attr("type")),
        element.attr("title"))
    } catch (e: Exception) {
      return null;
    }
  }
}