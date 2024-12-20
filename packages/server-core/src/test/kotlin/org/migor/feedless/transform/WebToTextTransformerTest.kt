package org.migor.feedless.transform

import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.migor.feedless.scrape.WebToTextTransformer

internal class WebToTextTransformerTest {

  private lateinit var extractor: WebToTextTransformer

  @BeforeEach
  fun up() {
    extractor = WebToTextTransformer()
  }

  @Test
  fun extractsSimple() = runTest {
    val actual = extractor.extractText(parse("<app-article>lorem ipsum <b>this</b> or <EM>that</em></app-article>"))
    assertEquals("lorem ipsum this or that", actual)
  }

  @Test
  fun extractsAndPreservesStructure() = runTest {
    val actual =
      extractor.extractText(parse("<article><header>the title</header><section>lorem ipsum</section> <a href=\"http://google.de\">a link</a> <p>that</p><footer>copyright nobody</footer></article>"))
    assertEquals(
      """the title
lorem ipsum
 a link that
copyright nobody""", actual
    )
  }

  private fun parse(markup: String): Element? {
    return Jsoup.parse(markup).select("body").first()
  }

}
