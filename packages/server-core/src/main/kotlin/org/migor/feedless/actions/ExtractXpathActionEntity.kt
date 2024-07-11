package org.migor.feedless.actions

import io.hypersistence.utils.hibernate.type.array.EnumArrayType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.ForeignKey
import jakarta.persistence.PrimaryKeyJoinColumn
import jakarta.persistence.Table
import org.hibernate.annotations.Type
import org.migor.feedless.generated.types.ScrapeEmit

@Entity
@Table(name = "t_action_extract_xpath")
@PrimaryKeyJoinColumn(
  foreignKey = ForeignKey(
    name = "fk_base_entity",
    foreignKeyDefinition = "FOREIGN KEY (id) REFERENCES t_scrape_action(id) ON DELETE CASCADE"
  )
)
open class ExtractXpathActionEntity : ScrapeActionEntity() {

  @Column(name = "fragment_name", nullable = false)
  open lateinit var fragmentName: String

  @Column(name = "xpath", nullable = false)
  open lateinit var xpath: String

  @Type(EnumArrayType::class)
  @Column(name = "emit", nullable = false, columnDefinition = "text[]")
  open var emit: Array<ExtractEmit> = emptyArray()
}

enum class ExtractEmit {
  text,
  html,
  pixel,
  date
}

fun ScrapeEmit.fromDto(): ExtractEmit {
  return when(this) {
    ScrapeEmit.text -> ExtractEmit.text
    ScrapeEmit.html -> ExtractEmit.html
    ScrapeEmit.pixel -> ExtractEmit.pixel
    ScrapeEmit.date -> ExtractEmit.date
  }
}

fun ExtractEmit.toDto(): ScrapeEmit {
  return when(this) {
    ExtractEmit.text -> ScrapeEmit.text
    ExtractEmit.html -> ScrapeEmit.html
    ExtractEmit.pixel -> ScrapeEmit.pixel
    ExtractEmit.date -> ScrapeEmit.date
  }
}