package org.migor.feedless.plan

import org.migor.feedless.data.jpa.enums.EntityVisibility
import java.util.*

interface PlanConstraintsService {

  fun coerceRetentionMaxItems(maxItems: Int?, userId: UUID): Int?

  fun coerceMinScheduledNextAt(nextDate: Date, userId: UUID): Date

  fun coerceRetentionMaxAgeDays(maxAge: Int?): Int?

  fun auditCronExpression(cronString: String): String

  fun coerceVisibility(visibility: EntityVisibility?): EntityVisibility

  fun auditScrapeRequestMaxActions(actionsCount: Int?, userId: UUID)

  fun auditScrapeRequestTimeout(timeout: Int?, userId: UUID)

  fun coerceScrapeSourceExpiry(corrId: String, userId: UUID): Date?

  fun auditScrapeSourceMaxCount(count: Int, userId: UUID)

  fun violatesScrapeSourceMaxActiveCount(userId: UUID): Boolean

  fun auditScrapeRequestMaxCountPerSource(count: Int, userId: UUID)
}