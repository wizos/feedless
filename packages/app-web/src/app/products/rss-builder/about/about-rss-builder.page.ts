import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  CUSTOM_ELEMENTS_SCHEMA,
  ElementRef,
  inject,
  OnInit,
  viewChild,
} from '@angular/core';
import { fixUrl } from '../../../app.module';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ServerConfigService } from '../../../services/server-config.service';
import { dateFormat } from '../../../services/session.service';
import { LicenseService } from '../../../services/license.service';
import { VerticalSpec } from '../../../all-verticals';
import { AppConfigService } from '../../../services/app-config.service';
import { LocalizedLicense } from '../../../graphql/types';
import { addIcons } from 'ionicons';
import { settingsOutline } from 'ionicons/icons';
import { IonContent, IonItem } from '@ionic/angular/standalone';
import { ProductHeaderComponent } from '../../../components/product-header/product-header.component';
import { SearchbarComponent } from '../../../elements/searchbar/searchbar.component';
import { ImportButtonComponent } from '../../../components/import-button/import-button.component';

@Component({
  selector: 'app-about-rss-builder',
  templateUrl: './about-rss-builder.page.html',
  styleUrls: ['./about-rss-builder.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  schemas: [CUSTOM_ELEMENTS_SCHEMA],
  imports: [
    IonContent,
    ProductHeaderComponent,
    SearchbarComponent,
    RouterLink,
    ImportButtonComponent,
    IonItem,
  ],
  standalone: true,
})
export class AboutRssBuilderPage implements OnInit {
  private readonly router = inject(Router);
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly changeRef = inject(ChangeDetectorRef);
  private readonly licenseService = inject(LicenseService);
  private readonly appConfigService = inject(AppConfigService);
  readonly serverConfig = inject(ServerConfigService);

  readonly opmlPickerElement =
    viewChild.required<ElementRef<HTMLInputElement>>('opmlPicker');

  protected readonly dateFormat = dateFormat;
  protected license: LocalizedLicense;
  protected product: VerticalSpec;

  constructor() {
    addIcons({ settingsOutline });
  }

  async handleQuery(url: string) {
    try {
      await this.router.navigate(['/feed-builder'], {
        queryParams: {
          url: fixUrl(url),
        },
      });
    } catch (e) {
      console.warn(e);
    }
  }

  getLicenseExpiry() {
    return new Date(
      this.serverConfig.getBuildFrom() + 1000 * 60 * 60 * 24 * 265 * 2,
    );
  }

  async ngOnInit() {
    const products = await this.appConfigService.getAllAppConfigs();
    const source = this.activatedRoute.snapshot.queryParams['source'];
    if (source) {
      await this.handleQuery(source);
    }
    this.product = products.find((app) => app.id === 'rss-proxy');
    this.changeRef.detectChanges();
    this.licenseService.licenseChange.subscribe((license) => {
      this.license = license;
      this.changeRef.detectChanges();
    });
  }
}
