import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmbeddedImageComponent } from './embedded-image.component';
import { AppTestModule } from '../../app-test.module';
import { SourceBuilder } from '../interactive-website/source-builder';
import { ScrapeService } from '../../services/scrape.service';

describe('EmbeddedImageComponent', () => {
  let component: EmbeddedImageComponent;
  let fixture: ComponentFixture<EmbeddedImageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmbeddedImageComponent, AppTestModule.withDefaults()],
    }).compileComponents();

    fixture = TestBed.createComponent(EmbeddedImageComponent);
    component = fixture.componentInstance;
    component.sourceBuilder = SourceBuilder.fromUrl(
      '',
      TestBed.inject(ScrapeService),
    );

    component.embed = {
      data: '',
      url: '',
      mimeType: '',
    };
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
