import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { SegmentedOutputComponent } from './segmented-output.component';
import { InputModule } from '../../elements/input/input.module';
import { IonGrid, IonRow, IonCol, IonButton } from '@ionic/angular/standalone';

@NgModule({
  exports: [SegmentedOutputComponent],
  imports: [
    CommonModule,
    InputModule,
    IonGrid,
    IonRow,
    IonCol,
    IonButton,
    SegmentedOutputComponent,
  ],
})
export class SegmentedOutputModule {}
