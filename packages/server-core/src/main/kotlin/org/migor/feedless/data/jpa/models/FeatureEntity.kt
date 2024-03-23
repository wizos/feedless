package org.migor.feedless.data.jpa.models

import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.migor.feedless.data.jpa.EntityWithUUID
import org.migor.feedless.data.jpa.StandardJpaFields
import org.migor.feedless.generated.types.Feature
import org.migor.feedless.generated.types.FeatureBooleanValue
import org.migor.feedless.generated.types.FeatureIntValue
import org.migor.feedless.generated.types.FeatureValue
import java.util.*
import org.migor.feedless.generated.types.FeatureName as FeatureNameDto

enum class FeatureName {
  // internal
//  authSSO,
//  authMail,

  rateLimitInt,
  minRefreshRateInMinutesInt,
  publicScrapeSourceBool,
  pluginsBool,

  scrapeRequestTimeoutInt,
  scrapeSourceRetentionMaxItemsInt,
  itemEmailForwardBool,
  itemWebhookForwardBool,
  apiBool,
  canLogin,
  canCreateUser,
  canCreateAsAnonymous,
  hasWaitList,
  canSignUp,
  scrapeSourceExpiryInDaysInt,
  scrapeSourceMaxCountActiveInt,
  scrapeRequestMaxCountPerSourceInt,
  scrapeRequestActionMaxCountInt,
  scrapeSourceMaxCountTotalInt,
}

fun featureScope(name: FeatureName): FeatureScope {
  return when (name) {
    FeatureName.canSignUp,
    FeatureName.canLogin,
    FeatureName.canCreateUser,
    FeatureName.canCreateAsAnonymous,
    FeatureName.hasWaitList -> FeatureScope.frontend

    FeatureName.rateLimitInt,
    FeatureName.minRefreshRateInMinutesInt,
    FeatureName.publicScrapeSourceBool,
    FeatureName.pluginsBool,
    FeatureName.scrapeRequestTimeoutInt,
    FeatureName.scrapeSourceRetentionMaxItemsInt,
    FeatureName.itemEmailForwardBool,
    FeatureName.itemWebhookForwardBool,
    FeatureName.apiBool,
    FeatureName.scrapeSourceExpiryInDaysInt,
    FeatureName.scrapeSourceMaxCountActiveInt,
    FeatureName.scrapeRequestMaxCountPerSourceInt,
    FeatureName.scrapeRequestActionMaxCountInt,
    FeatureName.scrapeSourceMaxCountTotalInt -> FeatureScope.backend
  }
}


@Entity
@Table(
  name = "t_feature", uniqueConstraints = [
    UniqueConstraint(name = "UniqueFeaturePerPlan", columnNames = [StandardJpaFields.planId, StandardJpaFields.name])]
)
open class FeatureEntity : EntityWithUUID() {

  @Basic
  @Column(nullable = false, name = StandardJpaFields.name, length = 50)
  @Enumerated(EnumType.STRING)
  open lateinit var name: FeatureName

  @Basic
  @Column(nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  open lateinit var scope: FeatureScope

  @Basic
  open var valueInt: Int? = null

  @Basic
  open var valueBoolean: Boolean? = null

  @Basic
  @Column(nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  open lateinit var valueType: FeatureValueType

  @Basic
  @Column(name = StandardJpaFields.planId, nullable = false)
  open lateinit var planId: UUID

  @ManyToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.CASCADE)
  @JoinColumn(
    name = StandardJpaFields.planId,
    referencedColumnName = "id",
    foreignKey = ForeignKey(name = "fk_feature__plan"),
    insertable = false,
    updatable = false
  )
  open var plan: PlanEntity? = null
}

fun FeatureEntity.toDto(): Feature {
  val value = FeatureValue.newBuilder()
  if (valueType == FeatureValueType.number) {
    value.numVal(
      FeatureIntValue.newBuilder()
        .value(valueInt!!)
        .build()
    )
  } else {
    value.boolVal(
      FeatureBooleanValue.newBuilder()
        .value(valueBoolean!!)
        .build()
    )
  }

  return Feature.newBuilder()
    .name(name.toDto())
    .value(value.build())
    .build()
}

fun FeatureName.toDto(): FeatureNameDto {
  return when (this) {
    FeatureName.pluginsBool -> FeatureNameDto.plugins
    FeatureName.rateLimitInt -> FeatureNameDto.rateLimit
    FeatureName.minRefreshRateInMinutesInt -> FeatureNameDto.minRefreshRateInMinutes
    FeatureName.publicScrapeSourceBool -> FeatureNameDto.publicScrapeSource
    FeatureName.scrapeRequestTimeoutInt -> FeatureNameDto.scrapeRequestTimeout
    FeatureName.scrapeSourceRetentionMaxItemsInt -> FeatureNameDto.scrapeSourceRetentionMaxItems
    FeatureName.itemEmailForwardBool -> FeatureNameDto.itemEmailForward
    FeatureName.itemWebhookForwardBool -> FeatureNameDto.itemWebhookForward
    FeatureName.scrapeSourceExpiryInDaysInt -> FeatureNameDto.scrapeSourceExpiryInDays
    FeatureName.scrapeSourceMaxCountActiveInt -> FeatureNameDto.scrapeSourceMaxCountActive
    FeatureName.scrapeRequestMaxCountPerSourceInt -> FeatureNameDto.scrapeRequestMaxCountPerSource
    FeatureName.scrapeRequestActionMaxCountInt -> FeatureNameDto.scrapeRequestActionMaxCount
    FeatureName.scrapeSourceMaxCountTotalInt -> FeatureNameDto.scrapeSourceMaxCountTotal
    FeatureName.apiBool -> FeatureNameDto.api
    FeatureName.canLogin -> FeatureNameDto.canLogin
    FeatureName.canCreateUser -> FeatureNameDto.canCreateUser
    FeatureName.canSignUp -> FeatureNameDto.canSignUp
    FeatureName.hasWaitList -> FeatureNameDto.hasWaitList
    FeatureName.canCreateAsAnonymous -> FeatureNameDto.canCreateAsAnonymous
  }
}
