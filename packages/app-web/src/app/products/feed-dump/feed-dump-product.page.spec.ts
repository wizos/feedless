import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { FeedDumpProductPage } from './feed-dump-product.page';
import {
  ApolloMockController,
  AppTestModule,
  mockLicense,
  mockScrape,
  mockServerSettings,
} from '../../app-test.module';
import { FeedDumpProductModule } from './feed-dump-product.module';
import { RouterTestingModule } from '@angular/router/testing';
import { ServerSettingsService } from '../../services/server-settings.service';
import { ApolloClient } from '@apollo/client/core';

describe('PcTrackerProductPage', () => {
  let component: FeedDumpProductPage;
  let fixture: ComponentFixture<FeedDumpProductPage>;

  beforeEach(waitForAsync(async () => {
    await TestBed.configureTestingModule({
      imports: [
        FeedDumpProductModule,
        AppTestModule.withDefaults((apolloMockController) => {
          mockScrape(apolloMockController);
          mockLicense(apolloMockController);
        }),
        RouterTestingModule.withRoutes([]),
      ],
    }).compileComponents();

    await mockServerSettings(
      TestBed.inject(ApolloMockController),
      TestBed.inject(ServerSettingsService),
      TestBed.inject(ApolloClient),
    );

    fixture = TestBed.createComponent(FeedDumpProductPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});