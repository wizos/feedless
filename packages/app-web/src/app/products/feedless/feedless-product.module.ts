import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';

import { IonicModule } from '@ionic/angular';

import { FeedlessProductRoutingModule } from './feedless-product-routing.module';

import { FeedlessProductPage } from './feedless-product.page';
import { ProductTitleModule } from '../../components/product-title/product-title.module';
import { DarkModeButtonModule } from '../../components/dark-mode-button/dark-mode-button.module';
import { LoginButtonModule } from '../../components/login-button/login-button.module';

@NgModule({
  imports: [
    CommonModule,
    IonicModule,
    FeedlessProductRoutingModule,
    ProductTitleModule,
    DarkModeButtonModule,
    LoginButtonModule,
  ],
  declarations: [FeedlessProductPage],
})
export class FeedlessProductModule {}