import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { ScrapeResponse } from '../../graphql/types';
import { ProductConfig, ProductService } from '../../services/product.service';
import { NotebookService } from './services/notebook.service';

@Component({
  selector: 'app-untold-notes-product-page',
  templateUrl: './untold-notes-product.page.html',
  styleUrls: ['./untold-notes-product.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UntoldNotesProductPage implements OnInit, OnDestroy {
  scrapeResponse: ScrapeResponse;
  productConfig: ProductConfig;
  private subscriptions: Subscription[] = [];

  constructor(
    private readonly productService: ProductService,
    private readonly notebookService: NotebookService,
    private readonly router: Router,
    private readonly changeRef: ChangeDetectorRef,
  ) {}

  async ngOnInit() {
    this.subscriptions.push(
      this.productService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }
}