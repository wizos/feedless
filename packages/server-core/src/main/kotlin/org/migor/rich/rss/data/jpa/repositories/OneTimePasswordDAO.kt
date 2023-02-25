package org.migor.rich.rss.data.jpa.repositories

import org.migor.rich.rss.data.jpa.models.OneTimePasswordEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface OneTimePasswordDAO : JpaRepository<OneTimePasswordEntity, UUID> {
  fun findByPassword(password: String): Optional<OneTimePasswordEntity>
}