package org.migor.feedless.community.text.simple

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import org.migor.feedless.AppProfiles
import org.migor.feedless.community.CommentEntity
import org.migor.feedless.community.LanguageService
import org.migor.feedless.community.PartOfSpeechService
import org.migor.feedless.community.StemmerService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.*

@Service
@Profile(AppProfiles.community)
class KeywordIntersectionScorer {

  private val log = LoggerFactory.getLogger(KeywordIntersectionScorer::class.simpleName)

  @Autowired
  lateinit var partOfSpeechService: PartOfSpeechService

  @Autowired
  lateinit var languageService: LanguageService

  @Autowired
  lateinit var stemmerService: StemmerService

  private var spline: PolynomialSplineFunction = createSplineInterpolator()

  fun score(parent: CommentEntity, child: CommentEntity): Double {
    return spline.value(calculateKeywordIntersection(parent, child))
  }

  fun calculateKeywordIntersection(parent: CommentEntity, child: CommentEntity): Double {
    val parentLocale = languageService.bestLocale(parent.contentText)
    val childLocale = languageService.bestLocale(child.contentText)

    return if (parentLocale.language != childLocale.language) {
      0.1
    } else {
      val parentKeywords = getKeywordsWIthFreq(parent, parentLocale)
      val childKeywords = getKeywordsWIthFreq(child, parentLocale)
      parentKeywords.filter { (word, _) -> childKeywords.any { (otherWord, _) -> otherWord == word} }
        .map { (_, freq) -> freq }
        .sum()
    }
  }

  private fun createSplineInterpolator(): PolynomialSplineFunction {
    val x = doubleArrayOf(0.0, 0.4, 1.0)
    val y = doubleArrayOf(0.0, 1.0, 1.0)

    return SplineInterpolator().interpolate(x, y)
  }

  private fun getKeywordsWIthFreq(source: CommentEntity, locale: Locale): Map<String, Double> {
    val text = source.contentText
    val nouns = partOfSpeechService.tag(text, locale)
      .filter { (_, tag) -> tag == "NOUN" || tag == "PROPN" }
    return nouns
      .map { (word, _) -> word.lowercase() }
      .let { stemmerService.stem(it, locale) }
      .groupBy { it }
      .mapValues { (_, v) -> v.size.toDouble() / nouns.size.toDouble() }
  }

}