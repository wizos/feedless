import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NotebookBuilderComponent } from './notebook-builder.component';
import { IonicModule } from '@ionic/angular';
import { FormsModule } from '@angular/forms';
import { ProductHeaderModule } from '../product-header/product-header.module';
import { SearchbarModule } from '../../elements/searchbar/searchbar.module';
import { RouterLink } from '@angular/router';

@NgModule({
  declarations: [NotebookBuilderComponent],
  exports: [NotebookBuilderComponent],
  imports: [
    CommonModule,
    IonicModule,
    FormsModule,
    ProductHeaderModule,
    SearchbarModule,
    RouterLink,
  ],
})
export class NotebookBuilderModule {}
