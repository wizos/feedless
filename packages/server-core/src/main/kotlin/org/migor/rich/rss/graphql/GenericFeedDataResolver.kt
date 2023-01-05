package org.migor.rich.rss.graphql

import com.netflix.graphql.dgs.DgsComponent
import com.netflix.graphql.dgs.DgsData
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment
import kotlinx.coroutines.coroutineScope
import org.migor.rich.rss.generated.GenericFeedDto
import org.migor.rich.rss.generated.NativeFeedDto
import org.migor.rich.rss.graphql.DtoResolver.toDTO
import org.migor.rich.rss.service.FeedService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Isolation
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.*

@DgsComponent
class GenericFeedDataResolver {

  @Autowired
  lateinit var feedService: FeedService

  @DgsData(parentType = "GenericFeed")
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_UNCOMMITTED)
  suspend fun nativeFeed(dfe: DgsDataFetchingEnvironment): NativeFeedDto? = coroutineScope {
    val feed: GenericFeedDto = dfe.getSource()
    feedService.findNativeById(UUID.fromString(feed.id)).map { toDTO(it) }.orElseThrow()
  }

}