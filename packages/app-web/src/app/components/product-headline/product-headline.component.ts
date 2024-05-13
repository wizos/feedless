import { Component, Input } from '@angular/core';

@Component({
  selector: 'app-product-headline',
  templateUrl: './product-headline.component.html',
  styleUrls: ['./product-headline.component.scss'],
})
export class ProductHeadlineComponent {
  @Input({ required: true })
  title: string;

  constructor() {}
}