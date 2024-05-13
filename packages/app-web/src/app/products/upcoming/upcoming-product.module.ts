import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { UpcomingProductRoutingModule } from './upcoming-product-routing.module';

import { UpcomingProductPage } from './upcoming-product-page.component';
import { DarkModeButtonModule } from '../../components/dark-mode-button/dark-mode-button.module';
import { SearchbarModule } from '../../elements/searchbar/searchbar.module';
import { BubbleModule } from '../../components/bubble/bubble.module';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    UpcomingProductRoutingModule,
    DarkModeButtonModule,
    SearchbarModule,
    BubbleModule,
  ],
  declarations: [UpcomingProductPage],
})
export class UpcomingProductModule {}