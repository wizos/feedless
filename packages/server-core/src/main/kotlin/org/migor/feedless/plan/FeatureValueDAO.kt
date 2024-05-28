package org.migor.feedless.plan

import org.migor.feedless.AppProfiles
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile(AppProfiles.database)
interface FeatureValueDAO : JpaRepository<FeatureValueEntity, UUID> {

  fun findByPlanIdAndFeatureId(planId: UUID, featureId: UUID): FeatureValueEntity?

  @Query(
    """
    select fv from FeatureValueEntity fv
    inner join PlanEntity p
    on fv.planId = p.id
    inner join FeatureEntity f
    on fv.featureId = f.id
    where p.name=:product and f.name=:feature
  """
  )
  fun findByProductNameAndFeatureName(
    @Param("product") product: String,
//    @Param("plan") plan: PlanName,
    @Param("feature") feature: String
  ): FeatureValueEntity

  @Query(
    """
    select FV from FeatureValueEntity FV
    inner join PlanEntity P
    on FV.planId = P.id
    where P.name = :product
  """
  )
  fun findAllByProductName(@Param("product") product: String): List<FeatureValueEntity>

  @Query(
    value = """
      WITH RECURSIVE plan_tree(id, parent_plan_id, depth) AS (
        SELECT t.id, t.parent_plan_id, 0
         FROM t_plan t
         where id = :planId
         UNION ALL
         SELECT t.id, t.parent_plan_id, depth + 1
         FROM t_plan t,
              plan_tree st
         WHERE t.id = st.parent_plan_id)
        SELECT distinct on (f.name) fv.id, fv.value_int, fv.value_bool, fv.value_type, fv.plan_id, fv.feature_id, fv.created_at
        FROM plan_tree pl
        inner join t_feature_value fv on pl.id = fv.plan_id
        inner join t_feature f on f.id = fv.feature_id
        where f.name = :feature
order by f.name, depth
  """,
    nativeQuery = true,
  )
  fun findByPlanIdAndName(@Param("planId") planId: UUID, @Param("feature") feature: String): List<FeatureValueEntity>

}
