import { EventEmitter } from '@angular/core';
import {
  GqlFeedlessPlugins,
  GqlHttpFetch,
  GqlHttpFetchInput,
  GqlHttpGetRequestInput,
  GqlScrapeActionInput,
  GqlScrapeEmit,
  GqlScrapeFlowInput,
  GqlScrapeRequest,
  GqlSourceInput,
} from '../../../generated/graphql';
import {
  BoundingBox,
  XyPosition,
} from '../embedded-image/embedded-image.component';
import { ScrapeResponse } from '../../graphql/types';
import { ReplaySubject } from 'rxjs';
import { ScrapeService } from '../../services/scrape.service';
import { cloneDeep, first, pick } from 'lodash-es';
import { FormControl, FormGroup } from '@angular/forms';
import { isDefined } from '../../types';

export type ScrapeControllerState = 'DIRTY' | 'PRISTINE';

function assertTrue(
  condition: boolean,
  error: string,
  flow: GqlScrapeActionInput[],
) {
  if (!condition) {
    throw new Error(`${error}, flow=${JSON.stringify(flow, null, 2)}`);
  }
}

export function getFirstFetchUrlLiteral(
  actions: { fetch?: GqlHttpFetch | GqlHttpFetchInput }[],
): string {
  return getFirstFetch(actions)?.get?.url?.literal;
}

export function getFirstFetch(
  actions: { fetch?: GqlHttpFetch | GqlHttpFetchInput }[],
): GqlHttpFetch {
  const fetchList = actions.filter((action) => isDefined(action.fetch));
  if (fetchList.length > 0) {
    return fetchList[0].fetch as GqlHttpFetch;
  }
}

export class SourceBuilder {
  events = {
    pickPoint: new EventEmitter<(position: XyPosition) => void>(),
    pickElement: new EventEmitter<(xpath: string) => void>(),
    pickArea: new EventEmitter<(bbox: BoundingBox) => void>(),
    actionsChanges: new EventEmitter<void>(),
    extractElements: new EventEmitter<{
      xpath: string;
      callback: (elements: HTMLElement[]) => void;
    }>(),
    showElements: new ReplaySubject<string>(),
    stateChange: new ReplaySubject<ScrapeControllerState>(),
    cancel: new EventEmitter<void>(),
  };

  response: ScrapeResponse;
  private flow: GqlScrapeActionInput[] = [];

  meta = new FormGroup({
    title: new FormControl<string>(''),
    tags: new FormControl<string[]>([]),
    localized: new FormControl<GqlSourceInput['localized']>(null),
  });

  static fromSource(
    source: GqlSourceInput,
    scrapeService: ScrapeService,
  ): SourceBuilder {
    return new SourceBuilder(scrapeService, source);
  }

  static fromUrl(url: string, scrapeService: ScrapeService) {
    const source = {
      title: `From ${url}`,
      tags: [],
      flow: {
        sequence: [
          {
            fetch: {
              get: {
                url: {
                  literal: url,
                },
              },
            },
          },
        ],
      },
    };

    return new SourceBuilder(scrapeService, source);
  }

  private constructor(
    private scrapeService: ScrapeService,
    source: GqlSourceInput,
  ) {
    this.patch(pick(source, ['localized', 'tags', 'title']));
    this.flow = cloneDeep(source.flow.sequence);
    this.validateFlow();
  }

  getUrl() {
    return getFirstFetchUrlLiteral(this.flow);
  }

  patchFetch(
    params: Partial<
      Pick<
        GqlHttpGetRequestInput,
        'additionalWaitSec' | 'url' | 'timeout' | 'language'
      >
    >,
  ) {
    const fetchAction = getFirstFetch(this.flow);

    Object.keys(params).forEach((key) => (fetchAction.get[key] = params[key]));
    this.events.stateChange.next('DIRTY');
    return this;
  }

  patch(param: Partial<Pick<GqlSourceInput, 'tags' | 'localized' | 'title'>>) {
    this.meta.patchValue(param);
    this.events.stateChange.next('DIRTY');
  }

  build(append: Array<GqlScrapeActionInput> = null): GqlScrapeRequest {
    return this.toSource({
      sequence: [...this.flow, ...(append ? append : [])],
    }) as GqlScrapeRequest;
  }

  removePluginById(pluginId: GqlFeedlessPlugins) {
    console.log('removePluginById', pluginId);
    if (this.flow) {
      this.flow = this.flow.filter((a) => a.execute?.pluginId !== pluginId);
      this.events.stateChange.next('DIRTY');
    }
    this.validateFlow();
    return this;
  }

  addOrUpdatePluginById(
    pluginId: GqlFeedlessPlugins,
    action: GqlScrapeActionInput,
  ) {
    console.log('updatePluginById', pluginId);
    const index = this.flow.findIndex((a) => a.execute?.pluginId === pluginId);
    if (index > -1) {
      this.flow[index] = action;
    } else {
      this.flow.push(action);
    }
    this.validateFlow();
    this.events.stateChange.next('DIRTY');
    return this;
  }

  findFirstByPluginsId(pluginId: GqlFeedlessPlugins): GqlScrapeActionInput {
    return first(this.findAllByPluginsId(pluginId));
  }

  findAllByPluginsId(pluginId: GqlFeedlessPlugins): GqlScrapeActionInput[] {
    return this.flow.filter((a) => a.execute?.pluginId === pluginId);
  }

  hasFetchActionReturnedHtml() {
    const contentType = this.response?.outputs?.find((o) => o.response.fetch)
      ?.response?.fetch?.debug?.contentType;
    return contentType?.toLowerCase()?.startsWith('text/html');
  }

  overwriteFlow(flow: GqlScrapeActionInput[]) {
    console.log('overwritePostFetchActions from -> ', this.flow);
    this.flow = flow;
    console.log('overwritePostFetchActions to -> ', this.flow);
    return this;
  }

  async fetchFeedsFromStatic() {
    return this.fetchFeeds('fetchFeedsFromStatic');
  }

  async fetchFeedsFromBrowser() {
    return this.fetchFeeds('fetchFeedsFromBrowser', {
      extract: {
        fragmentName: 'full-page',
        selectorBased: {
          fragmentName: '',
          xpath: {
            value: '/',
          },
          emit: [GqlScrapeEmit.Html, GqlScrapeEmit.Text, GqlScrapeEmit.Pixel],
        },
      },
    });
  }

  private async fetchFeeds(title: string, action: GqlScrapeActionInput = null) {
    console.log(
      'fetchFeeds',
      this.flow,
      '->',
      this.flow.filter((a) => !isDefined(a.execute)),
    );

    this.validateFlow();
    this.response = await this.scrapeService.scrape(
      this.toSource(
        {
          sequence: [
            ...this.flow.filter((a) => !isDefined(a.execute)),
            ...(action ? [action] : []),
            {
              execute: {
                pluginId: GqlFeedlessPlugins.OrgFeedlessFeeds,
                params: {},
              },
            },
          ],
        },
        title,
      ),
    );
    this.events.stateChange.next('DIRTY');
    return this.response;
  }

  private toSource(
    flow: GqlScrapeFlowInput,
    title: string = null,
  ): GqlSourceInput {
    return {
      corrId: '',
      id: '',
      title: title || this.meta.value.title,
      localized: this.meta.value.localized,
      tags: this.meta.value.tags,
      flow,
    };
  }

  private validateFlow() {
    const fetchActionCount = this.flow.filter((a) => isDefined(a.fetch)).length;
    assertTrue(
      fetchActionCount === 1,
      `Invalid number of fetch actions ${fetchActionCount}`,
      this.flow,
    );

    assertTrue(
      this.findAllByPluginsId(GqlFeedlessPlugins.OrgFeedlessFeed).length <= 1,
      'too many feed plugins',
      this.flow,
    );
    assertTrue(
      this.findAllByPluginsId(GqlFeedlessPlugins.OrgFeedlessFeeds).length <= 1,
      'too many feeds plugins',
      this.flow,
    );
  }
}

export function findAllByPluginsIdIn(
  source: GqlSourceInput,
  pluginIds: GqlFeedlessPlugins[],
): GqlScrapeActionInput[] {
  return source.flow.sequence.filter((a) =>
    pluginIds.includes(a.execute?.pluginId as any),
  );
}

export function findAllByPluginsIdNotIn(
  source: GqlSourceInput,
  pluginIds: GqlFeedlessPlugins[],
): GqlScrapeActionInput[] {
  return source.flow.sequence.filter(
    (a) => !pluginIds.includes(a.execute?.pluginId as any),
  );
}