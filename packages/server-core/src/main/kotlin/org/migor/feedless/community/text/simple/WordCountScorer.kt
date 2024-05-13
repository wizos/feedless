package org.migor.feedless.community.text.simple

import org.apache.commons.math3.analysis.interpolation.SplineInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction
import org.migor.feedless.AppProfiles
import org.migor.feedless.community.TokenizerService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.util.*

@Service
@Profile(AppProfiles.community)
class WordCountScorer {

  private val log = LoggerFactory.getLogger(WordCountScorer::class.simpleName)

  @Autowired
  lateinit var tokenizerService: TokenizerService

  private var spline: PolynomialSplineFunction = createSplineInterpolator()

  fun score(text: String, locale: Locale): Double {
    val wc = tokenizerService.tokenizeWords(text, locale).size
    return spline.value(wc.toDouble())
  }

  private fun createSplineInterpolator(): PolynomialSplineFunction {
    val minVal = 1.0
    val q1 = 8.33184
    val q2 = 53.05969880602388
    val q3 = 133.97448
    val maxVal = 500.0

    // Define the interpolation points
    val x = doubleArrayOf(minVal, q1, q2, q3, maxVal, 700.0, 10000.0)
    val y = doubleArrayOf(0.0, 0.3, 1.0, 0.7, 0.6, 0.5, 0.5)

    // Perform cubic spline interpolation
    return SplineInterpolator().interpolate(x, y)
  }
}