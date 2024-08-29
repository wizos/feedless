package org.migor.feedless.source

import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*
import java.util.stream.Stream

@Repository
@Profile(AppProfiles.database)
interface SourceDAO : JpaRepository<SourceEntity, UUID> {
  fun findAllByRepositoryIdOrderByCreatedAtDesc(id: UUID): List<SourceEntity>

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  @Modifying
  @Query(
    """
      update SourceEntity C
        set C.erroneous = :erroneous,
            C.lastErrorMessage = :errorMessage
      where C.id = :id
    """
  )
  fun setErrorState(
    @Param("id") id: UUID,
    @Param("erroneous") erroneous: Boolean,
    @Param("errorMessage") errorMessage: String? = null
  )

  fun streamAllByRepositoryIdAndErroneousIsFalse(id: UUID): Stream<SourceEntity>
}