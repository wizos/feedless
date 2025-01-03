import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AppTestModule } from '../../app-test.module';
import { FeedBuilderModalComponent } from './feed-builder-modal.component';
import { FeedBuilderModalModule } from './feed-builder-modal.module';

describe('FeedBuilderModalComponent', () => {
  let component: FeedBuilderModalComponent;
  let fixture: ComponentFixture<FeedBuilderModalComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FeedBuilderModalModule, AppTestModule.withDefaults()],
    }).compileComponents();

    fixture = TestBed.createComponent(FeedBuilderModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
