import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { TrackerDetailsPage } from './tracker-details.page';
import { AppTestModule } from '../../../app-test.module';

describe('TrackerDetailsPage', () => {
  let component: TrackerDetailsPage;
  let fixture: ComponentFixture<TrackerDetailsPage>;

  beforeEach(waitForAsync(async () => {
    await TestBed.configureTestingModule({
      imports: [TrackerDetailsPage, AppTestModule.withDefaults()],
    }).compileComponents();

    fixture = TestBed.createComponent(TrackerDetailsPage);
    component = fixture.componentInstance;
    component.repository = {} as any;
    component.documents = [];
    fixture.detectChanges();
  }));

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
