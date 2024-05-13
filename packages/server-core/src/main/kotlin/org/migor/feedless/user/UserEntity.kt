package org.migor.feedless.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.migor.feedless.agent.AgentEntity
import org.migor.feedless.data.jpa.EntityWithUUID
import org.migor.feedless.data.jpa.StandardJpaFields
import org.migor.feedless.data.jpa.enums.AuthSource
import org.migor.feedless.data.jpa.enums.ProductName
import org.migor.feedless.generated.types.User
import org.migor.feedless.plan.PlanEntity
import org.migor.feedless.secrets.OneTimePasswordEntity
import org.migor.feedless.secrets.UserSecretEntity
import org.migor.feedless.subscription.UserPlanSubscriptionEntity
import java.sql.Timestamp
import java.util.*

@Entity
@Table(
  name = "t_user",
  uniqueConstraints = [
    UniqueConstraint(name = "UniqueUser", columnNames = [StandardJpaFields.email, StandardJpaFields.product])]
)
open class UserEntity : EntityWithUUID() {

  @Column(name = StandardJpaFields.email)
  open var email: String? = null

  open var githubId: String? = null

  @Column(nullable = false, name = "has_validated_email")
  open var hasValidatedEmail: Boolean = false

  @Column(name = "validated_email_at")
  open var validatedEmailAt: Timestamp? = null

  @Column(nullable = false, length = 50)
  @Enumerated(EnumType.STRING)
  open lateinit var usesAuthSource: AuthSource

  @Column(nullable = false, name = StandardJpaFields.product)
  open lateinit var product: ProductName

  @Column(nullable = false, name = "is_root")
  open var root: Boolean = false

  @Column(nullable = false, name = "is_anonymous")
  open var anonymous: Boolean = false

  @Column(nullable = false, name = "last_login")
  open var lastLogin: Timestamp = Timestamp(System.currentTimeMillis())

  @Column(nullable = false, name = "karma")
  open var karma: Int = 0

  @Column(nullable = false, name = "is_spamming_submissions")
  open var spammingSubmissions: Boolean = false

  @Column(nullable = false, name = "is_spamming_votes")
  open var spammingVotes: Boolean = false

  @Column(nullable = false, name = "is_shaddow_banned")
  open var shaddowBanned: Boolean = false

  @Column(nullable = false, name = "is_banned")
  open var banned: Boolean = false

  @Column(name = "is_banned_until")
  open var bannedUntil: Timestamp? = null

  @Column(nullable = false, name = "hasapprovedterms")
  open var hasAcceptedTerms: Boolean = false

  @Column(name = "approved_terms_at")
  open var acceptedTermsAt: Timestamp? = null

  @Column(nullable = false, name = "is_locked")
  open var locked: Boolean = false

  @Column(name = "purge_scheduled_for")
  open var purgeScheduledFor: Timestamp? = null

  @Column(name = "date_format")
  open var dateFormat: String? = null // todo make nullable=false

  @Column(name = "time_format")
  open var timeFormat: String? = null

  @Column(name = StandardJpaFields.planId, nullable = true)
  open var planId: UUID? = null

  @ManyToOne(fetch = FetchType.LAZY)
  @OnDelete(action = OnDeleteAction.NO_ACTION)
  @JoinColumn(
    name = StandardJpaFields.planId,
    referencedColumnName = "id",
    insertable = false,
    updatable = false
  )
  open var plan: PlanEntity? = null

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "userId")
  @OnDelete(action = OnDeleteAction.NO_ACTION)
  open var oneTimePasswords: MutableList<OneTimePasswordEntity> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "userId")
  @OnDelete(action = OnDeleteAction.NO_ACTION)
  open var planSubscriptions: MutableList<UserPlanSubscriptionEntity> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "ownerId", orphanRemoval = true)
  @OnDelete(action = OnDeleteAction.NO_ACTION)
  open var userSecrets: MutableList<UserSecretEntity> = mutableListOf()

  @OneToMany(fetch = FetchType.LAZY, mappedBy = "ownerId", orphanRemoval = true)
  @OnDelete(action = OnDeleteAction.NO_ACTION)
  open var agents: MutableList<AgentEntity> = mutableListOf()
}


fun UserEntity.toDto(): User =
  User.newBuilder()
    .id(id.toString())
    .createdAt(createdAt.time)
    .purgeScheduledFor(purgeScheduledFor?.time)
    .hasAcceptedTerms(hasAcceptedTerms)
//          .dateFormat(propertyService.dateFormat)
//          .timeFormat(propertyService.timeFormat)
//          .minimalFeatureState(FeatureState.experimental)

    .build()