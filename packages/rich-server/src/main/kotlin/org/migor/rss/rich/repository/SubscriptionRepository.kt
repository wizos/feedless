package org.migor.rss.rich.repository

import org.migor.rss.rich.model.Subscription
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*
import javax.transaction.Transactional


@Repository
interface SubscriptionRepository: PagingAndSortingRepository<Subscription, String> {
  fun findAllByNextHarvestAtBeforeOrNextHarvestAtIsNull(now: Date, pageable: Pageable): List<Subscription>

  fun findAllByNextEntryReleaseAtBefore(nextEntryReleaseAt: Date, pageable: Pageable): List<Subscription>

  @Transactional
  @Modifying
  @Query("update Subscription s set s.nextHarvestAt = :nextHarvestAt where s.id = :id")
  fun updateNextHarvestAt(@Param("id") subscriptionId: String, @Param("nextHarvestAt") nextHarvestAt: Date)

  @Transactional
  @Modifying
  @Query("update Subscription s set s.nextEntryReleaseAt = :nextReleaseAt where s.id = :id")
  fun updateNextEntryReleaseAt(@Param("id") subscriptionId: String, @Param("nextReleaseAt") nextReleasAt: Date)
}
