package org.migor.feedless.user

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang3.StringUtils
import org.migor.feedless.AppLayer
import org.migor.feedless.AppMetrics
import org.migor.feedless.AppProfiles
import org.migor.feedless.BadRequestException
import org.migor.feedless.NotFoundException
import org.migor.feedless.PermissionDeniedException
import org.migor.feedless.data.jpa.enums.EntityVisibility
import org.migor.feedless.data.jpa.enums.Vertical
import org.migor.feedless.feature.FeatureName
import org.migor.feedless.feature.FeatureService
import org.migor.feedless.generated.types.UpdateCurrentUserInput
import org.migor.feedless.plan.ProductDAO
import org.migor.feedless.plan.ProductService
import org.migor.feedless.repository.MaxAgeDaysDateField
import org.migor.feedless.repository.RepositoryDAO
import org.migor.feedless.repository.RepositoryEntity
import org.migor.feedless.session.RequestContext
import org.migor.feedless.transport.TelegramBotService
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.core.env.Environment
import org.springframework.core.env.Profiles
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.jvm.optionals.getOrNull

@Service
@Transactional(propagation = Propagation.NEVER)
@Profile("${AppProfiles.user} & ${AppLayer.service}")
class UserService(
  private var userDAO: UserDAO,
  private var productDAO: ProductDAO,
  private var meterRegistry: MeterRegistry,
  private var environment: Environment,
  private var featureService: FeatureService,
  private var repositoryDAO: RepositoryDAO,
  private var productService: ProductService,
  private var githubConnectionService: GithubConnectionDAO,
  private var connectedAppDAO: ConnectedAppDAO,
  @Lazy
  private var telegramBotService: Optional<TelegramBotService>
) {

  private val log = LoggerFactory.getLogger(UserService::class.simpleName)

  @Transactional
  suspend fun createUser(
    email: String?,
    githubId: String? = null,
  ): UserEntity {
    if (featureService.isDisabled(FeatureName.canCreateUser, null)) {
      throw BadRequestException("sign-up is deactivated")
    }
//    val plan = planDAO.findByNameAndProductId(planName.name, productName)
//    plan ?: throw BadRequestException("plan $planName for product $productName does not exist")
//
//    if (plan.availability == PlanAvailability.unavailable) {
//      throw BadRequestException("plan $planName for product $productName is unavailable")
//    }
    return withContext(Dispatchers.IO) {
      if (StringUtils.isNotBlank(email)) {
        if (userDAO.existsByEmail(email!!)) {
          throw BadRequestException("user already exists")
        }
      }
      if (StringUtils.isNotBlank(githubId)) {
        if (githubConnectionService.existsByGithubId(githubId!!)) {
          throw BadRequestException("user already exists")
        }

      }
      meterRegistry.counter(AppMetrics.userSignup, listOf(Tag.of("type", "user"))).increment()
      log.debug("[${coroutineContext.corrId()}] create user")
      val user = UserEntity()
      user.email = email ?: fallbackEmail(user)
      user.admin = false
      user.anonymous = false
      user.hasAcceptedTerms = isSelfHosted()

      val savedUser = userDAO.save(user)

      if (githubId != null) {
        linkGithubAccount(savedUser, githubId)
      }
      createInboxRepository(savedUser)

      // todo saas only?
      productService.enableDefaultSaasProduct(Vertical.feedless, savedUser.id)
      savedUser
    }
  }

  @Transactional
  suspend fun createInboxRepository(user: UserEntity): RepositoryEntity {
    val r = RepositoryEntity()
    r.title = "Notifications"
    r.description = ""
    r.sourcesSyncCron = ""
    r.product = Vertical.all
    r.ownerId = user.id
    r.retentionMaxCapacity = 1000
    r.retentionMaxAgeDays = null
    r.visibility = EntityVisibility.isPrivate
    r.retentionMaxAgeDaysReferenceField = MaxAgeDaysDateField.createdAt

    return withContext(Dispatchers.IO) {
      val savedRepository = repositoryDAO.save(r)

      user.inboxRepositoryId = r.id
      userDAO.save(user)

      savedRepository
    }
  }

  @Transactional(readOnly = true)
  suspend fun findByEmail(email: String): UserEntity? {
    return withContext(Dispatchers.IO) {
      userDAO.findByEmail(email)
    }
  }

  @Transactional(readOnly = true)
  suspend fun findByGithubId(githubId: String): UserEntity? {
    return withContext(Dispatchers.IO) {
      userDAO.findByGithubId(githubId) ?: userDAO.findByEmail("$githubId@github.com")
    }
  }

  @Transactional
  suspend fun updateUser(userId: UUID, data: UpdateCurrentUserInput) {
    val user = withContext(Dispatchers.IO) {
      userDAO.findById(userId).orElseThrow { NotFoundException("user not found") }
    }

    var changed = false

    val corrId = coroutineContext.corrId()
    data.email?.let {
      log.info("[$corrId] changing email from ${user.email} to ${it.set}")
      user.email = it.set
      user.validatedEmailAt = null
      user.hasValidatedEmail = false
      // todo ask to validate email
      changed = true
    }

    data.firstName?.let {
      user.firstName = it.set
      changed = true
    }
    data.lastName?.let {
      user.lastName = it.set
      changed = true
    }
    data.country?.let {
      user.country = it.set
      changed = true
    }

    data.plan?.let {
      val product = withContext(Dispatchers.IO) { productDAO.findById(UUID.fromString(it.set)).orElseThrow() }
      productService.enableSaasProduct(
        product,
        user
      )
    }

    data.acceptedTermsAndServices?.let {
      if (it.set) {
        user.hasAcceptedTerms = true
        user.acceptedTermsAt = LocalDateTime.now()
        log.debug("[$corrId] accepted terms")
      } else {
        log.debug("[$corrId] rejecting hasAcceptedTerms")
        user.hasAcceptedTerms = false
        user.acceptedTermsAt = null
      }
      changed = true
    }
    data.purgeScheduledFor?.let {
      if (it.assignNull) {
        user.purgeScheduledFor = null
        log.info("[$corrId] unset purgeScheduledFor")
      } else {
        user.purgeScheduledFor = LocalDateTime.now().plusDays(30)
        log.info("[$corrId] set purgeScheduledFor")
      }
      changed = true
    }
    if (changed) {
      withContext(Dispatchers.IO) {
        userDAO.save(user)
      }
    } else {
      log.debug("[$corrId] unchanged")
    }
  }

  @Transactional(readOnly = true)
  suspend fun getAnonymousUser(): UserEntity {
    return withContext(Dispatchers.IO) {
      userDAO.findByAnonymousIsTrue()
    }
  }

  @Transactional
  suspend fun updateLegacyUser(user: UserEntity, githubId: String) {
    log.info("[${coroutineContext.corrId()}] update legacy user githubId=$githubId")

    val isGithubAccountLinked = withContext(Dispatchers.IO) {
      githubConnectionService.existsByUserId(user.id)
    }

    if (!isGithubAccountLinked) {
      linkGithubAccount(user, githubId)
    }

    if (user.email.trim().endsWith("github.com")) {
      user.email = fallbackEmail(user)
    }

    withContext(Dispatchers.IO) {
      userDAO.save(user)
    }
  }

  @Transactional(readOnly = true)
  suspend fun findById(userId: UUID): Optional<UserEntity> {
    return withContext(Dispatchers.IO) {
      userDAO.findById(userId)
    }
  }

  @Transactional(readOnly = true)
  suspend fun getConnectedAppByUserAndId(userId: UUID, connectedAppId: UUID): ConnectedAppEntity {
    return withContext(Dispatchers.IO) {
      connectedAppDAO.findByIdAndUserIdEquals(connectedAppId, userId)
        ?: connectedAppDAO.findByIdAndAuthorizedEqualsAndUserIdIsNull(connectedAppId, false)
        ?: throw IllegalArgumentException("not found")
    }
  }

  @Transactional
  suspend fun updateConnectedApp(userId: UUID, connectedAppId: UUID, authorize: Boolean) {
    withContext(Dispatchers.IO) {
      val app =
        getConnectedAppByUserAndId(userId, connectedAppId)
      app.userId?.let {
        if (userId != it) {
          throw PermissionDeniedException("error")
        }
      }

      app.authorized = authorize
      app.authorizedAt = LocalDateTime.now()
      app.userId = userId

      connectedAppDAO.save(app)
      telegramBotService.getOrNull()?.let {
        if (app is TelegramConnectionEntity) {
          it.showOptionsForKnownUser(app.chatId)
        }
      }
    }
  }

  @Transactional
  suspend fun deleteConnectedApp(currentUserId: UUID, connectedAppId: UUID) {
    withContext(Dispatchers.IO) {
      val app =
        connectedAppDAO.findByIdAndAuthorizedEquals(connectedAppId, true) ?: throw IllegalArgumentException("not found")
//      app.userId?.let {
      if (currentUserId != app.userId) {
        throw PermissionDeniedException("error")
      }
//      }

      if (app is TelegramConnectionEntity) {
        telegramBotService.getOrNull()?.let { it.sendMessage(app.chatId, "Disconnected") }
      } else {
        throw IllegalArgumentException("github connection cannot be removed")
      }


      connectedAppDAO.delete(app)
    }
  }

  private fun fallbackEmail(user: UserEntity) = "${user.id}@feedless.org"

  private fun isSelfHosted() = environment.acceptsProfiles(Profiles.of(AppProfiles.selfHosted))

  private suspend fun linkGithubAccount(user: UserEntity, githubId: String) {
    val githubLink = GithubConnectionEntity()
    githubLink.userId = user.id
    githubLink.githubId = githubId
    githubLink.authorized = true
    githubLink.authorizedAt = LocalDateTime.now()

    withContext(Dispatchers.IO) {
      githubConnectionService.save(githubLink)
    }
  }

  @Transactional(readOnly = true)
  suspend fun findAdminUser(): UserEntity? {
    return withContext(Dispatchers.IO) {
      userDAO.findFirstByAdminIsTrue()
    }
  }
}

fun CoroutineContext.corrId(): String? {
  return this[RequestContext]?.corrId
}

fun CoroutineContext.userIdOptional(): UUID? {
  return this[RequestContext]?.userId
}

fun CoroutineContext.userId(): UUID {
  return this.userIdOptional()!!
}
