import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';

import { FeedlessProductPage } from './feedless-product.page';

import { DefaultRoutes } from '../default-routes';
import { FeedlessMenuComponent } from './feedless-menu/feedless-menu.component';
import { AuthGuardService } from '../../guards/auth-guard.service';

const routes: Routes = [
  ...DefaultRoutes,
  {
    path: '',
    component: FeedlessProductPage,
    children: [
      {
        path: 'builder',
        loadChildren: () =>
          import('../../pages/feed-builder/feed-builder.module').then(
            (m) => m.FeedBuilderPageModule,
          ),
      },
      {
        path: 'agents',
        canActivate: [AuthGuardService],
        loadChildren: () =>
          import('../../pages/agents/agents.module').then(
            (m) => m.AgentsPageModule,
          ),
      },
      {
        path: 'repositories',
        canActivate: [AuthGuardService],
        loadChildren: () =>
          import('../../pages/repositories/repositories.module').then(
            (m) => m.RepositoriesPageModule,
          ),
      },
      // {
      //   path: 'plans',
      //   loadChildren: () =>
      //     import('./plans/plans.module').then((m) => m.PlansPageModule),
      // },
      {
        path: 'products',
        loadChildren: () =>
          import('./products/products.module').then(
            (m) => m.ProductsPageModule,
          ),
      },
      {
        path: 'buy',
        loadChildren: () =>
          import('../../pages/buy/buy.module').then(
            (m) => m.BuyPageModule,
          ),
      },
      {
        path: '',
        loadChildren: () =>
          import('./about/about-feedless.module').then(
            (m) => m.AboutFeedlessModule,
          ),
      },
      {
        path: '**',
        redirectTo: '',
      },
    ],
  },
  {
    path: '',
    outlet: 'sidemenu',
    component: FeedlessMenuComponent,
  },
  {
    path: '**',
    redirectTo: '/',
  },
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class FeedlessProductRoutingModule {}
