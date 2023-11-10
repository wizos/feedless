import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ScrapeSourceComponent } from './scrape-source.component';
import { EmbeddedWebsiteModule } from '../embedded-website/embedded-website.module';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { IonicModule } from '@ionic/angular';
import { SelectModule } from '../select/select.module';
import { MenuModule } from '../menu/menu.module';
import { SharedModule } from 'primeng/api';
import { SplitterModule } from 'primeng/splitter';
import { InputTextModule } from 'primeng/inputtext';
import { EmbeddedImageModule } from '../embedded-image/embedded-image.module';
import { RouterLink } from '@angular/router';
import { Select2Module } from '../select2/select2.module';
import { ScrapeSourceStepModule } from '../scrape-source-step/scrape-source-step.module';
import { TransformWebsiteToFeedModule } from '../transform-website-to-feed/transform-website-to-feed.module';


@NgModule({
  declarations: [ScrapeSourceComponent],
  exports: [ScrapeSourceComponent],
  imports: [
    CommonModule,
    EmbeddedWebsiteModule,
    FormsModule,
    IonicModule,
    SelectModule,
    Select2Module,
    MenuModule,
    SharedModule,
    SplitterModule,
    InputTextModule,
    ReactiveFormsModule,
    EmbeddedImageModule,
    RouterLink,
    ScrapeSourceStepModule,
    TransformWebsiteToFeedModule
  ]
})
export class ScrapeSourceModule { }
