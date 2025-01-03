import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  inject,
  OnDestroy,
  OnInit,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { LocalizedLicense } from '../../graphql/types';
import {
  AppConfigService,
  VerticalSpecWithRoutes,
} from '../../services/app-config.service';
import { ServerConfigService } from '../../services/server-config.service';
import { dateFormat } from '../../services/session.service';
import { LicenseService } from '../../services/license.service';
import { ModalService } from '../../services/modal.service';
import { TrackerEditModalComponentProps } from './tracker-edit/tracker-edit-modal.component';
import { addIcons } from 'ionicons';
import { logoGithub } from 'ionicons/icons';
import {
  IonButton,
  IonButtons,
  IonChip,
  IonContent,
  IonFooter,
  IonHeader,
  IonIcon,
  IonMenuButton,
  IonRouterOutlet,
  IonToolbar,
} from '@ionic/angular/standalone';
import { TrialWarningComponent } from '../../components/trial-warning/trial-warning.component';

import { DarkModeButtonComponent } from '../../components/dark-mode-button/dark-mode-button.component';
import { LoginButtonComponent } from '../../components/login-button/login-button.component';
import { TrackerEditModalModule } from './tracker-edit/tracker-edit-modal.module';

@Component({
  selector: 'app-change-tracker-product-page',
  templateUrl: './change-tracker-product.page.html',
  styleUrls: ['./change-tracker-product.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    IonHeader,
    TrialWarningComponent,
    IonToolbar,
    IonButtons,
    IonMenuButton,
    RouterLink,
    IonButton,
    IonIcon,
    DarkModeButtonComponent,
    LoginButtonComponent,
    IonContent,
    IonRouterOutlet,
    IonFooter,
    IonChip,
    TrackerEditModalModule,
  ],
  standalone: true,
})
export class ChangeTrackerProductPage implements OnInit, OnDestroy {
  private readonly activatedRoute = inject(ActivatedRoute);
  private readonly appConfigService = inject(AppConfigService);
  private readonly licenseService = inject(LicenseService);
  private readonly modalService = inject(ModalService);
  readonly serverConfig = inject(ServerConfigService);
  private readonly router = inject(Router);
  private readonly changeRef = inject(ChangeDetectorRef);

  productConfig: VerticalSpecWithRoutes;
  url: string;
  private subscriptions: Subscription[] = [];
  license: LocalizedLicense;

  constructor() {
    addIcons({ logoGithub });
  }

  async ngOnInit() {
    this.subscriptions.push(
      this.appConfigService
        .getActiveProductConfigChange()
        .subscribe((productConfig) => {
          this.productConfig = productConfig;
          this.changeRef.detectChanges();
        }),
      this.licenseService.licenseChange.subscribe((license) => {
        this.license = license;
        this.changeRef.detectChanges();
      }),
      this.activatedRoute.queryParams.subscribe((queryParams) => {
        if (queryParams.url) {
          this.url = queryParams.url;
        }
      }),
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  protected readonly dateFormat = dateFormat;

  async openCreateTrackerModal() {
    const props: TrackerEditModalComponentProps = {};
    await this.modalService.openTrackerEditor(props);
  }
}
