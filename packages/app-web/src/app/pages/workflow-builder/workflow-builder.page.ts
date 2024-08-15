import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Subscription } from 'rxjs';
import {
  AppConfigService,
  ProductConfig,
} from '../../services/app-config.service';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-workflow-builder-page',
  templateUrl: './workflow-builder.page.html',
  styleUrls: ['./workflow-builder.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WorkflowBuilderPage implements OnInit, OnDestroy {
  loading = false;
  productConfig: ProductConfig;
  private subscriptions: Subscription[] = [];

  constructor(
    private readonly appConfigService: AppConfigService,
    private readonly titleService: Title,
    private readonly changeRef: ChangeDetectorRef,
  ) {}

  async ngOnInit() {
    this.titleService.setTitle('Workflow Builder');
    this.subscriptions.push(
      this.appConfigService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  // async handleFeed(feed: FeedWithRequest) {
  //   const { title, description } = this.getFeedData(
  //     feed.feed,
  //     getFirstFetchUrlLiteral(feed.scrapeRequest.page.actions),
  //   );
  //   const componentProps: GenerateFeedModalComponentProps = {
  //     repository: {
  //       title,
  //       description,
  //       plugins: [],
  //       sources: [
  //         getScrapeRequest(feed.feed, feed.scrapeRequest as GqlScrapeRequest),
  //       ],
  //     } as any,
  //   };
  //   await this.modalService.openFeedMetaEditor(componentProps);
  // }
  //
  // private getFeedData(feed: NativeOrGenericFeed, urlString: string) {
  //   if (feed.nativeFeed) {
  //     return {
  //       title: feed.nativeFeed.title,
  //       description: `Source: ${feed.nativeFeed.feedUrl}`,
  //     };
  //   } else {
  //     const url = new URL(urlString);
  //     return {
  //       title: `Feed from ${url.host}${url.pathname}`,
  //       description: `Source: ${url}`,
  //     };
  //   }
  // }
}
