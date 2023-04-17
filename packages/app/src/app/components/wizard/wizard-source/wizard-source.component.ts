import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  Input,
  OnInit,
  Output,
} from '@angular/core';
import { WizardStepId } from '../wizard/wizard.component';
import {
  FeedService,
  TransientNativeFeed,
} from '../../../services/feed.service';
import { ModalController } from '@ionic/angular';
import { GqlFeatureName, GqlVisibility } from '../../../../generated/graphql';
import { WizardHandler } from '../wizard-handler';

@Component({
  selector: 'app-wizard-source',
  templateUrl: './wizard-source.component.html',
  styleUrls: ['./wizard-source.component.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class WizardSourceComponent implements OnInit {
  @Input()
  handler: WizardHandler;

  @Output()
  navigateTo: EventEmitter<WizardStepId> = new EventEmitter<WizardStepId>();
  feedFromPageChange = GqlFeatureName.GenFeedFromPageChange;

  effectiveWebsiteUrl: string | undefined;
  currentWebsiteUrl: string | undefined;

  constructor(
    private readonly feedService: FeedService,
    private readonly changeRef: ChangeDetectorRef,
    private readonly modalCtrl: ModalController
  ) {}

  ngOnInit() {
    this.handler.onContextChange().subscribe(async (changes) => {
      if (this.currentWebsiteUrl !== this.handler.getDiscovery()?.websiteUrl) {
        this.currentWebsiteUrl = this.handler.getDiscovery().websiteUrl;
        this.effectiveWebsiteUrl = this.handler.getDiscovery().document.url;
        this.changeRef.detectChanges();
      }
      if (changes.busy) {
        this.changeRef.detectChanges();
      }
    });
  }

  isWebsite(): boolean {
    return this.handler.hasMimeType('text/html');
  }

  async startFeedDiscoveryFlow(): Promise<void> {
    this.navigateTo.emit(WizardStepId.feeds);
  }

  async startPageChangedFlow(): Promise<void> {
    this.navigateTo.emit(WizardStepId.pageChange);
  }

  closeModal(): Promise<boolean> {
    return this.modalCtrl.dismiss();
  }

  isFeed(): boolean {
    return (
      this.handler.hasMimeType('application/atom+xml') ||
      this.handler.hasMimeType('application/xml') ||
      this.handler.hasMimeType('text/xml')
    );
  }

  isSourceSupported(): boolean {
    return this.isFeed() || this.isWebsite();
  }

  mimetype(): string {
    return this.handler.getDiscovery().document.mimeType;
  }

  // async startExistingNativeFeedRefinementFlow(nativeFeed: BasicNativeFeed) {
  //   await this.handler.updateContext({
  //     feed: {
  //       connect: {
  //         id: nativeFeed.id,
  //       },
  //     },
  //   });
  //   this.navigateTo.emit(WizardStepId.refineNativeFeed);
  // }

  async startCreateNativeFeedRefinementFlow(nativeFeed: TransientNativeFeed) {
    await this.handler.updateContext({
      isCurrentStepValid: true,
      feed: {
        create: {
          nativeFeed: {
            feedUrl: nativeFeed.url,
            title: nativeFeed.title,
            description: nativeFeed.description,
            autoRelease: true,
            harvestItems: false,
            harvestSiteWithPrerender: false,
            visibility: GqlVisibility.IsPublic,
          },
        },
      },
    });
    this.navigateTo.emit(WizardStepId.refineNativeFeed);
  }

  isRedirected(): boolean {
    return (
      this.getHostname(this.currentWebsiteUrl) !==
      this.getHostname(this.effectiveWebsiteUrl)
    );
  }

  getHostname(url: string): string {
    return new URL(url).hostname;
  }

  // private async searchNativeFeeds() {
  //   await this.feedService
  //     .searchNativeFeeds({
  //       where: {
  //         query: this.currentWebsiteUrl,
  //       },
  //       page: this.pagination ? this.pagination.page + 1 : 0,
  //     })
  //     .then((response) => {
  //       this.matchingFeeds.push(...response.nativeFeeds);
  //       this.pagination = response.pagination;
  //       this.changeRef.detectChanges();
  //     });
  // }
}