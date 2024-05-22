package org.migor.feedless.pipeline

import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.*
import java.util.stream.Stream

@Repository
@Profile(AppProfiles.database)
interface PipelineJobDAO : JpaRepository<PipelineJobEntity, UUID> {
  @Query(
    nativeQuery = true,
    value = """
      select p.* from t_pipeline_job p
      where p.terminated = false
      and p.document_id in (
        select g.document_id
        from (
            select distinct on (document_id)
                    document_id, cool_down_until
            from t_pipeline_job
            where terminated = false
            order by document_id, sequence_id
        ) g
      where g.cool_down_until is null
         or g.cool_down_until < current_timestamp)
      order by document_id, sequence_id
      limit 100
    """
  )
  fun findAllPendingBatched(): List<PipelineJobEntity>

  fun findAllBySchemaVersionNot(schemaVersion: Int): Stream<PipelineJobEntity>

}
