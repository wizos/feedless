import { Injectable } from '@angular/core';
import {
  GqlApiUrls,
  GqlFeatureName,
  GqlFeatureToggle,
  GqlServerSettingsQuery,
  GqlServerSettingsQueryVariables,
  ServerSettings
} from '../../generated/graphql';
import { ApolloClient } from '@apollo/client/core';

export type FeatureToggle = Pick<GqlFeatureToggle, 'name' | 'enabled'>;

export type ApiUrls = Pick<GqlApiUrls, 'webToFeed'>;

@Injectable({
  providedIn: 'root',
})
export class ServerSettingsService {
  private featureToggles: Array<FeatureToggle>;
  private apiUrls: ApiUrls;
  constructor(private readonly apollo: ApolloClient<any>) {}

  async fetchServerSettings(): Promise<void> {
    const { featureToggles, apiUrls } = await this.apollo
      .query<GqlServerSettingsQuery, GqlServerSettingsQueryVariables>({
        query: ServerSettings,
        variables: {
        },
      }).then(response => response.data.serverSettings);
    this.featureToggles = featureToggles;
    this.apiUrls = apiUrls;
  }

  hasFeature(featureName: GqlFeatureName) {
    return this.featureToggles.find(ft => ft.name === featureName)?.enabled || false;
  }

  getApiUrls(): ApiUrls {
    return this.apiUrls;
  }
}