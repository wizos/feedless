package org.migor.feedless.data.jpa.models

import com.vladmihalcea.hibernate.type.json.JsonType
import jakarta.persistence.Basic
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.Index
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.PrePersist
import jakarta.persistence.Table
import org.apache.commons.lang3.StringUtils
import org.hibernate.annotations.Type
import org.migor.feedless.data.jpa.EntityWithUUID
import org.migor.feedless.data.jpa.StandardJpaFields
import org.migor.feedless.data.jpa.enums.ReleaseStatus
import java.util.*

@Entity
@Table(
  name = "t_web_document", indexes = [
    Index(name = "idx_web_document_url", columnList = "url")
  ]
)
open class WebDocumentEntity : EntityWithUUID() {
  private fun getContentOfMime(mime: String): String? {
    return if (mime == this.contentRawMime) {
      StringUtils.trimToNull(this.contentRaw)
    } else {
      null
    }
  }

  fun contentHtml(): String? {
    return getContentOfMime("text/html")
  }

  companion object {
    const val LEN_TITLE = 256
    const val LEN_URL = 1000
  }

  @Basic
  @Column(nullable = false, length = LEN_URL)
  open lateinit var url: String

  @Basic
  @Column(length = LEN_URL)
  open var aliasUrl: String? = null

  @Basic
  @Column(nullable = false)
  open var hasAudio: Boolean = false

  @Basic
  @Column(nullable = false)
  open var hasVideo: Boolean = false

  @Basic
  @Column(length = LEN_TITLE)
  open var contentTitle: String? = null
    set(value) {
      field = StringUtils.substring(value, 0, NativeFeedEntity.LEN_TITLE)
    }

  @Basic
  @Column
  open var contentRawMime: String? = null

  @Column(columnDefinition = "TEXT") // bytea
  @Basic(fetch = FetchType.LAZY)
  open var contentRaw: String? = null

  @Column(columnDefinition = "TEXT")
  open var contentText: String? = null

  @Basic
  @Column(length = LEN_URL)
  open var imageUrl: String? = null

  @Basic
  @Column(nullable = false)
  open lateinit var updatedAt: Date

  @Basic
  @Column(nullable = false, name = StandardJpaFields.releasedAt)
  open lateinit var releasedAt: Date

  @Basic
  open var startingAt: Date? = null

  @Basic
  @Column(nullable = false)
  open var score: Int = 0

  @Type(JsonType::class)
  @Column(columnDefinition = "jsonb", nullable = false, name = "pending_plugins")
  @Basic(fetch = FetchType.LAZY)
  open var pendingPlugins: List<String> = emptyList()

  @Type(JsonType::class)
  @Column(columnDefinition = "jsonb", nullable = false, name = "executed_plugins")
  @Basic(fetch = FetchType.LAZY)
  open var executedPlugins: List<String> = emptyList()

  @Basic
  open var pluginsCoolDownUntil: Date? = null

  @Basic
  @Column(nullable = false)
  open var finalized: Boolean = false

  @Type(JsonType::class)
  @Column(columnDefinition = "jsonb")
  open var attachments: WebDocumentAttachments? = null

//  @OneToMany(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "from")
//  open var hyperLink: MutableList<HyperLinkEntity> = mutableListOf()
//
//  @OneToOne(fetch = FetchType.LAZY, cascade = [CascadeType.ALL], mappedBy = "content")
//  open var harvestTask: HarvestWebDocumentEntity? = null

//  @OneToMany(fetch = FetchType.LAZY, cascade = [], mappedBy = "webDocument")
//  open var articles: MutableList<ArticleEntity> = mutableListOf()

  @Basic
  @Column(name = "subscriptionId", nullable = false)
  open lateinit var subscriptionId: UUID

  @ManyToOne(fetch = FetchType.LAZY, cascade = [])
  @JoinColumn(name = "subscriptionId", referencedColumnName = "id", insertable = false, updatable = false, foreignKey = ForeignKey(name = "fk_item__subscritpion"))
  open var subscription: SourceSubscriptionEntity? = null

  @Basic
  @Column(nullable = false, name = StandardJpaFields.status)
  @Enumerated(EnumType.STRING)
  open var status: ReleaseStatus = ReleaseStatus.released

//  @ManyToMany(fetch = FetchType.LAZY, cascade = [CascadeType.PERSIST])
//  @JoinTable(
//    name = "content_to_tag",
//    joinColumns = [
//      JoinColumn(
//        name = "content_id", referencedColumnName = "id",
//        nullable = false, updatable = false
//      )],
//    inverseJoinColumns = [
//      JoinColumn(
//        name = "tag_id", referencedColumnName = "id",
//        nullable = false, updatable = false
//      )
//    ]
//  )
//  open var tags: List<TagEntity> = mutableListOf()

  @PrePersist
  fun prePersist() {
    attachments?.let {
      hasAudio = it.media.any {it.format?.contains("audio") ?: false}
      hasVideo = it.media.any {it.format?.contains("video") ?: false}
    } ?: run {
      hasAudio = false
      hasVideo = false
    }
    this.finalized = pendingPlugins.isEmpty()
  }

}

