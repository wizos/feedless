package org.migor.feedless.annotation

import jakarta.persistence.Column
import jakarta.persistence.DiscriminatorValue
import jakarta.persistence.Entity
import jakarta.persistence.PrePersist
import jakarta.persistence.Table

@Entity
@Table(name = "t_annotation_vote")
@DiscriminatorValue("vote")
open class VoteEntity : AnnotationEntity() {
  @Column(nullable = false, name = "is_upvote")
  open var upVote: Boolean = false

  @Column(nullable = false, name = "is_downvote")
  open var downVote: Boolean = false

  @Column(nullable = false, name = "is_flag")
  open var flag: Boolean = false

  @PrePersist
  fun prePersist() {
    val trueValues = arrayOf(upVote, downVote, flag).filter { it }
    if (trueValues.size != 1) {
      throw IllegalArgumentException("invalid flags")
    }
  }
}
