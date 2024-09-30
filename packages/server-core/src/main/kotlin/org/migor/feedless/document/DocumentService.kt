package org.migor.feedless.document

import com.linecorp.kotlinjdsl.dsl.jpql.jpql
import com.linecorp.kotlinjdsl.querymodel.jpql.path.Path
import com.linecorp.kotlinjdsl.querymodel.jpql.predicate.Predicatable
import com.linecorp.kotlinjdsl.render.jpql.JpqlRenderContext
import com.linecorp.kotlinjdsl.support.spring.data.jpa.extension.createQuery
import jakarta.persistence.EntityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.asynchttpclient.exception.TooManyConnectionsPerHostException
import org.migor.feedless.AppLayer
import org.migor.feedless.AppProfiles
import org.migor.feedless.PermissionDeniedException
import org.migor.feedless.ResumableHarvestException
import org.migor.feedless.data.jpa.enums.ReleaseStatus
import org.migor.feedless.generated.types.CreateRecordInput
import org.migor.feedless.generated.types.DatesWhereInput
import org.migor.feedless.generated.types.RecordDateField
import org.migor.feedless.generated.types.RecordFrequency
import org.migor.feedless.generated.types.RecordOrderByInput
import org.migor.feedless.generated.types.RecordUniqueWhereInput
import org.migor.feedless.generated.types.RecordUpdateInput
import org.migor.feedless.generated.types.RecordsWhereInput
import org.migor.feedless.generated.types.StringFilter
import org.migor.feedless.pipeline.DocumentPipelineJobDAO
import org.migor.feedless.pipeline.DocumentPipelineJobEntity
import org.migor.feedless.pipeline.FeedlessPlugin
import org.migor.feedless.pipeline.FilterEntityPlugin
import org.migor.feedless.pipeline.MapEntityPlugin
import org.migor.feedless.pipeline.PipelineJobStatus
import org.migor.feedless.pipeline.PluginService
import org.migor.feedless.pipeline.plugins.asJsonItem
import org.migor.feedless.plan.PlanConstraintsService
import org.migor.feedless.repository.MaxAgeDaysDateField
import org.migor.feedless.repository.RepositoryDAO
import org.migor.feedless.repository.RepositoryEntity
import org.migor.feedless.scrape.LogCollector
import org.migor.feedless.session.PermissionService
import org.migor.feedless.user.UserEntity
import org.migor.feedless.user.corrId
import org.migor.feedless.util.toLocalDateTime
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.coroutines.coroutineContext
import kotlin.jvm.optionals.getOrNull


@Service
@Profile("${AppProfiles.document} & ${AppLayer.service}")
@Transactional
class DocumentService(
  private val documentDAO: DocumentDAO,
  private val transactionManager: PlatformTransactionManager,
  private val entityManager: EntityManager,
  private val repositoryDAO: RepositoryDAO,
  private val planConstraintsService: PlanConstraintsService,
  private val documentPipelineJobDAO: DocumentPipelineJobDAO,
  private val pluginService: PluginService,
  private val permissionService: PermissionService
) {

  private val log = LoggerFactory.getLogger(DocumentService::class.simpleName)


//  @Autowired
//  private lateinit var mailService: MailService
//
//  @Autowired
//  private lateinit var mailForwardDAO: MailForwardDAO


  suspend fun findById(id: UUID): DocumentEntity? {
    return withContext(Dispatchers.IO) {
      documentDAO.findById(id).getOrNull()
    }
  }

  suspend fun findAllByRepositoryId(
    repositoryId: UUID,
    where: RecordsWhereInput? = null,
    orderBy: RecordOrderByInput? = null,
    status: ReleaseStatus = ReleaseStatus.released,
    tag: String? = null,
    pageable: Pageable,
    shareKey: String? = null,
    ignoreVisibility: Boolean = false
  ): Page<DocumentEntity?> {
    return withContext(Dispatchers.IO) {
      documentDAO.findPage(pageable) {
        val whereStatements = prepareWhereStatements(where)

        select(
          entity(DocumentEntity::class),
        ).from(
          entity(DocumentEntity::class),
        )
          .whereAnd(
            path(DocumentEntity::repositoryId).eq(repositoryId),
            path(DocumentEntity::status).`in`(status),
            path(DocumentEntity::publishedAt).lt(LocalDateTime.now()),
            *whereStatements.toTypedArray()
          ).orderBy(
            orderBy?.let {
              path(DocumentEntity::startingAt).asc().nullsLast()
            } ?: path(DocumentEntity::publishedAt).desc()
          )
      }
    }
  }

  private fun prepareWhereStatements(where: RecordsWhereInput?): MutableList<Predicatable> {
    val whereStatements = mutableListOf<Predicatable>()
    jpql {
      val addDateConstraint = { it: DatesWhereInput, field: Path<LocalDateTime> ->
        it.before?.let {
          whereStatements.add(field.le(it.toLocalDateTime()))
        }
        it.after?.let {
          whereStatements.add(field.ge(it.toLocalDateTime()))
        }
      }

      where?.let {
        it.source?.let { whereStatements.add(path(DocumentEntity::sourceId).eq(UUID.fromString(it.id))) }
        it.startedAt?.let { addDateConstraint(it, path(DocumentEntity::startingAt)) }
        it.createdAt?.let { addDateConstraint(it, path(DocumentEntity::createdAt)) }
        it.updatedAt?.let { addDateConstraint(it, path(DocumentEntity::updatedAt)) }
        it.publishedAt?.let { addDateConstraint(it, path(DocumentEntity::publishedAt)) }
        it.latLng?.let {
          // https://postgis.net/docs/ST_Distance.html
          whereStatements.add(path(DocumentEntity::latLon).isNotNull())
          whereStatements.add(
            function(
              Double::class,
              "fl_latlon_distance",
              path(DocumentEntity::latLon),
              doubleLiteral(it.near.lat),
              doubleLiteral(it.near.lon)
            )
              .lt(doubleLiteral(it.distanceKm))
          )
        }
      }
      // dummy
      select(expression<String>("")).from(entity(DocumentEntity::class))
    }

    return whereStatements
  }

  suspend fun applyRetentionStrategy(repositoryId: UUID) {
    val repository = withContext(Dispatchers.IO) {
      repositoryDAO.findById(repositoryId).orElseThrow()
    }
    val retentionSize =
      planConstraintsService.coerceRetentionMaxCapacity(
        repository.retentionMaxCapacity,
        repository.ownerId,
        repository.product
      )

    val corrId = coroutineContext.corrId()
    if (retentionSize != null && retentionSize > 0) {
      log.debug("[$corrId] applying retention with maxItems=$retentionSize")
      withContext(Dispatchers.IO) {
        val transactionTemplate = TransactionTemplate(transactionManager)
        transactionTemplate.executeWithoutResult {
          runBlocking {
            documentDAO.deleteAllByRepositoryIdAndStatusWithSkip(repository.id, ReleaseStatus.released, retentionSize)
          }
        }
      }
    } else {
      log.debug("[$corrId] no retention with maxItems given")
    }

    planConstraintsService.coerceRetentionMaxAgeDays(
      repository.retentionMaxAgeDays,
      repository.ownerId,
      repository.product
    )
      ?.let { maxAgeDays ->
        withContext(Dispatchers.IO) {
          log.debug("[$corrId] applying retention with maxAgeDays=$maxAgeDays")
          val maxDate = LocalDateTime.now().minus(maxAgeDays.toLong(), ChronoUnit.DAYS)
          if (repository.retentionMaxAgeDaysReferenceField == MaxAgeDaysDateField.startingAt) {
            documentDAO.deleteAllByRepositoryIdAndStartingAtBeforeAndStatus(
              repository.id,
              maxDate,
              ReleaseStatus.released
            )
          } else {
            if (repository.retentionMaxAgeDaysReferenceField == MaxAgeDaysDateField.createdAt) {
              documentDAO.deleteAllByRepositoryIdAndCreatedAtBeforeAndStatus(
                repository.id,
                maxDate,
                ReleaseStatus.released
              )
            } else {
              documentDAO.deleteAllByRepositoryIdAndPublishedAtBeforeAndStatus(
                repository.id,
                maxDate,
                ReleaseStatus.released
              )
            }
          }
        }

      } ?: log.debug("[$corrId] no retention with maxAgeDays given")
  }

  suspend fun deleteDocuments(user: UserEntity, repositoryId: UUID, documentIds: StringFilter) {
    log.info("[${coroutineContext.corrId()}] deleteDocuments $documentIds")
    val repository = withContext(Dispatchers.IO) { repositoryDAO.findById(repositoryId).orElseThrow() }
    if (repository.ownerId != user.id) {
      throw PermissionDeniedException("current user ist not owner")
    }

    withContext(Dispatchers.IO) {
      val transactionTemplate = TransactionTemplate(transactionManager)
      transactionTemplate.executeWithoutResult {
        runBlocking {
          if (documentIds.`in` != null) {
            documentDAO.deleteAllByRepositoryIdAndIdIn(repositoryId, documentIds.`in`.map { UUID.fromString(it) })
          } else {
            if (documentIds.eq != null) {
              documentDAO.deleteAllByRepositoryIdAndId(repositoryId, UUID.fromString(documentIds.eq))
            } else {
              throw IllegalArgumentException("operation not supported")
            }
          }
        }
      }
    }
  }

  suspend fun getRecordFrequency(
    where: RecordsWhereInput,
    groupBy: RecordDateField,
  ): List<RecordFrequency> {
    val query = jpql {
      val whereStatements = prepareWhereStatements(where)
      val dateGroup = expression<Long>("day")

      val groupByEntity = when (groupBy) {
        RecordDateField.createdAt -> path(DocumentEntity::createdAt)
        RecordDateField.publishedAt -> path(DocumentEntity::publishedAt)
        RecordDateField.startingAt -> path(DocumentEntity::startingAt)
      }

      selectNew<Pair<Long, Long>>(
        count(path(DocumentEntity::id)),
        function(Long::class, "fl_trunc_timestamp_as_millis", groupByEntity).`as`(dateGroup)
      ).from(
        entity(DocumentEntity::class),
      )
        .whereAnd(
          groupByEntity.isNotNull(),
          path(DocumentEntity::repositoryId).eq(UUID.fromString(where.repository.id)),
          path(DocumentEntity::publishedAt).lt(LocalDateTime.now()),
          *whereStatements.toTypedArray()
        )
        .groupBy(dateGroup)
    }

    val context = JpqlRenderContext()
    return withContext(Dispatchers.IO) {
      val q = entityManager.createQuery(query, context)
      q.resultList.map { pair -> RecordFrequency(pair.first.toInt(), pair.second) }
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  suspend fun processDocumentPlugins(documentId: UUID, jobs: List<DocumentPipelineJobEntity>) {
    val corrId = coroutineContext.corrId()
    log.debug("[$corrId] ${jobs.size} processPlugins for document $documentId")
    val document = withContext(Dispatchers.IO) { documentDAO.findByIdWithSource(documentId) }
    val logCollector = LogCollector()

    document?.let {
      try {
        val repository = withContext(Dispatchers.IO) {
          repositoryDAO.findById(document.repositoryId).orElseThrow()
        }
        var withError = false

        jobs.takeWhile { job ->
          try {
            if (job.attempt > 10) {
              throw IllegalArgumentException("max attempts reached")
            }

            when (val plugin = pluginService.resolveById<FeedlessPlugin>(job.executorId)) {
              is FilterEntityPlugin -> if (!plugin.filterEntity(
                  document.asJsonItem(),
                  job.executorParams,
                  0,
                  logCollector
                )
              ) {
                throw FilterMismatchException()
              }

              is MapEntityPlugin -> plugin.mapEntity(document, repository, job.executorParams, logCollector)
              else -> throw IllegalArgumentException("Invalid executorId ${job.executorId}")
            }

            log.debug("[$corrId] executed ${job.executorId} for $documentId")
            job.status = PipelineJobStatus.SUCCEEDED
            job.updateStatus()
            withContext(Dispatchers.IO) {
              documentPipelineJobDAO.save(job)
            }
            true
          } catch (e: Exception) {
            withError = true
            if (e is ResumableHarvestException || e is TooManyConnectionsPerHostException) {
              delayJob(job, e, document)
            } else {
              if (e !is FilterMismatchException) {
                log.warn("[$corrId] ${e.message}")
              }
              deleteDocument(document)
            }
            false
          }
        }

        if (!withError) {
          releaseDocument(document, repository)
        }

      } catch (throwable: Throwable) {
        log.warn("[$corrId] aborting pipeline for document, cause ${throwable.message}")
        deleteDocument(document)
      }
    } ?: withContext(Dispatchers.IO) {
      log.warn("[$corrId] delete remainging jobs")
      documentPipelineJobDAO.deleteAllByDocumentId(documentId)
    }

  }

  private suspend fun DocumentService.releaseDocument(
    document: DocumentEntity,
    repository: RepositoryEntity
  ) {
//    forwardToMail(corrId, document, repository)
    document.status = ReleaseStatus.released
    log.debug("[${coroutineContext.corrId()}] releasing document")
    withContext(Dispatchers.IO) {
      documentDAO.save(document)
    }
    applyRetentionStrategy(repository.id)
  }

  private suspend fun deleteDocument(document: DocumentEntity) {
    log.debug("[${coroutineContext.corrId()}] delete document ${document.id}")
    withContext(Dispatchers.IO) {
      documentDAO.delete(document)
    }
  }

  private suspend fun delayJob(
    job: DocumentPipelineJobEntity,
    e: Exception,
    document: DocumentEntity,
  ) {
    log.debug("[${coroutineContext.corrId()}] delaying (${job.executorId}): ${e.message}")

    val coolDownUntil = if (e is ResumableHarvestException) {
      LocalDateTime.now().plus(e.nextRetryAfter)
    } else {
      LocalDateTime.now().plusSeconds(120)
    }
    job.coolDownUntil = coolDownUntil

    job.attempt += 1
    withContext(Dispatchers.IO) {
      documentPipelineJobDAO.save(job)
      documentDAO.save(document)
    }
  }

  suspend fun countByRepositoryId(repositoryId: UUID): Long {
    return withContext(Dispatchers.IO) {
      documentDAO.countByRepositoryId(repositoryId)
    }
  }

  suspend fun createDocument(data: CreateRecordInput): DocumentEntity {
    val repositoryId = UUID.fromString(data.repositoryId.id)

    val repository = withContext(Dispatchers.IO) {
      repositoryDAO.findById(repositoryId).orElseThrow()
    }
    permissionService.canWrite(repository)

    val document = DocumentEntity()
    document.id = data.id?.let { UUID.fromString(data.id) } ?: UUID.randomUUID()
    document.title = data.title
    document.url = data.url
    document.text = data.text!!
    document.repositoryId = repositoryId
    document.status = ReleaseStatus.released

    return withContext(Dispatchers.IO) {
      documentDAO.save(document)
    }
  }

  suspend fun updateDocument(data: RecordUpdateInput, where: RecordUniqueWhereInput): DocumentEntity {
    return withContext(Dispatchers.IO) {
      val document = documentDAO.findById(UUID.fromString(where.id)).orElseThrow()
      permissionService.canWrite(document)
      data.url?.let {
        document.url = it.set
      }
      data.text?.let {
        document.text = it.set
      }
      data.title?.let {
        document.title = it.set
      }
      document.updatedAt = LocalDateTime.now()
      documentDAO.save(document)
    }
  }

//  private suspend fun forwardToMail(document: DocumentEntity, repository: RepositoryEntity) {
//    val mailForwards = withContext(Dispatchers.IO) {
//      mailForwardDAO.findAllByRepositoryId(repository.id)
//    }
//    if (mailForwards.isNotEmpty()) {
//      val authorizedMailForwards =
//        mailForwards.filterTo(ArrayList()) { it: MailForwardEntity -> it.authorized }.map { it.email }
//      if (authorizedMailForwards.isEmpty()) {
//        log.warn("[$corrId] no authorized mail-forwards available of ${mailForwards.size}")
//      } else {
//        val (mailFormatter, params) = pluginService.resolveMailFormatter(repository)
//        log.debug("[$corrId] using formatter ${mailFormatter::class.java.name}")
//
//        val from = mailService.getNoReplyAddress(repository.product)
//        val to = authorizedMailForwards.toTypedArray()
//
//        log.debug("[$corrId] resolved mail recipients [${authorizedMailForwards.joinToString(", ")}]")
//        mailService.send(
//          corrId,
//          from,
//          to,
//          mailFormatter.provideDocumentMail(corrId, document, repository, params)
//        )
//      }
//    }
//  }

}

class FilterMismatchException : RuntimeException()

