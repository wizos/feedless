import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { IonicModule } from '@ionic/angular';

import { FeedPageRoutingModule } from './feed-routing.module';

import { FeedPage } from './feed.page';
import { ArticleRefModule } from '../../components/article-ref/article-ref.module';
import { NativeFeedModule } from '../../components/native-feed/native-feed.module';

@NgModule({
  imports: [
    CommonModule,
    FormsModule,
    IonicModule,
    FeedPageRoutingModule,
    ArticleRefModule,
    NativeFeedModule,
  ],
  declarations: [FeedPage],
})
export class FeedPageModule {}
