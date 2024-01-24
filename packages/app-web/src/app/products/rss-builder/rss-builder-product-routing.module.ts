import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { RssBuilderProductPage } from './rss-builder-product.page';
import { ProductService } from '../../services/product.service';

const routes: Routes = [
  {
    path: '',
    component: RssBuilderProductPage,
    children: [
      {
        path: '',
        loadChildren: () =>
          import('./about/about-rss-builder.module').then(
            (m) => m.AboutRssBuilderModule)
      },
      {
        path: 'builder',
        loadChildren: () =>
          import('../../pages/feed-builder/feed-builder.module').then(
            (m) => m.FeedBuilderPageModule)
      },
      {
        path: 'feeds/:feedId',
        loadChildren: () =>
          import('./feed-details/feed-details.module').then(
            (m) => m.FeedDetailsPageModule)
      }
    ]
  },
  ...ProductService.defaultRoutes,
  {
    path: '**',
    redirectTo: '/'
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class RssBuilderPageRoutingModule {
}
