import {
  ChangeDetectionStrategy,
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit,
  ViewChild,
} from '@angular/core';
import { AppConfigService } from '../../../services/app-config.service';
import dayjs, { Dayjs, OpUnitType } from 'dayjs';
import { OpenStreetMapService } from '../../../services/open-street-map.service';
import { groupBy, sortBy, unionBy, uniqBy } from 'lodash-es';
import { RecordService } from '../../../services/record.service';
import { Record } from '../../../graphql/types';
import {
  BreadcrumbList,
  Event as SchemaEvent,
  Place as SchemaPlace,
  WebPage,
} from 'schema-dts';
import { getCachedLocations } from '../places';
import { LatLon } from '../../../components/map/map.component';
import { PageService, PageTags } from '../../../services/page.service';
import { ActivatedRoute } from '@angular/router';
import { Location } from '@angular/common';
import {
  homeRoute,
  parseDateFromUrl,
  parseLocationFromUrl,
} from '../upcoming-product-routing.module';
import { Subscription } from 'rxjs';
import 'dayjs/locale/de';
import { addIcons } from 'ionicons';
import {
  arrowBackOutline,
  arrowForwardOutline,
  sendOutline,
} from 'ionicons/icons';
import { NamedLatLon, Nullable } from '../../../types';
import { UpcomingHeaderComponent } from '../upcoming-header/upcoming-header.component';

type Distance2Events = { [distance: string]: Record[] };
type EventsByDistance = {
  distance: string;
  events: Record[];
};

type PlaceByDistance = {
  distance: number;
  places: EventsAtPlace[];
};

type EventsAtPlace = {
  place: NamedLatLon;
  events: Record[];
};

function roundLatLon(v: number): number {
  return Math.round(v * 1000) / 1000;
}

export function createBreadcrumbsSchema(loc: NamedLatLon): BreadcrumbList {
  return {
    '@type': 'BreadcrumbList',
    itemListElement: [
      {
        '@type': 'ListItem',
        position: 1,
        item: {
          '@id': 'https://example.com/dresses',
          name: 'Events',
        },
      },
      {
        '@type': 'ListItem',
        position: 2,
        item: {
          '@id': 'https://example.com/dresses',
          name: loc.area,
        },
      },
      {
        '@type': 'ListItem',
        position: 2,
        item: {
          '@id': 'https://example.com/dresses',
          name: loc.displayName,
        },
      },
    ],
  };
}

@Component({
  selector: 'app-events-page',
  templateUrl: './events.page.html',
  styleUrls: ['./events.page.scss'],
  changeDetection: ChangeDetectionStrategy.OnPush,
  standalone: false,
})
export class EventsPage implements OnInit, OnDestroy {
  date: Dayjs = dayjs();
  now: Dayjs = dayjs();
  perimeter: number = 10;
  latLon: Nullable<LatLon>;
  location: Nullable<NamedLatLon>;
  loading: boolean = true;
  dateWindow: Dayjs[] = [];
  private subscriptions: Subscription[] = [];

  @ViewChild('header')
  headerComponent: UpcomingHeaderComponent;

  protected placesByDistance: PlaceByDistance[] = [];
  protected loadingDay = true;
  protected eventCount: number = 0;

  constructor(
    private readonly activatedRoute: ActivatedRoute,
    private readonly recordService: RecordService,
    private readonly changeRef: ChangeDetectorRef,
    private readonly locationService: Location,
    private readonly pageService: PageService,
    private readonly openStreetMapService: OpenStreetMapService,
    private readonly appConfigService: AppConfigService,
  ) {
    addIcons({ arrowBackOutline, arrowForwardOutline, sendOutline });
  }

  async ngOnInit(): Promise<void> {
    this.pageService.setMetaTags(this.getPageTags());
    this.subscriptions.push(
      this.activatedRoute.params.subscribe(async (params) => {
        try {
          this.location = await parseLocationFromUrl(
            this.activatedRoute,
            this.openStreetMapService,
          );
          this.saveLocation(this.location);

          this.latLon = [this.location.lat, this.location.lon];

          const { perimeter } =
            homeRoute.children.events.children.countryCode.children.region.children.place.children.dateTime.parseParams(
              params as any,
            );

          this.perimeter = perimeter || 10;
          this.changeDate(parseDateFromUrl(params));

          this.changeRef.detectChanges();

          this.pageService.setMetaTags(this.getPageTags());

          await this.fetchEvents(this.date);
        } catch (e) {
          // todo save and retrieve last place window.localStorage.getItem('lastPlace')
          // const currentLocation = await firstValueFrom(
          //   this.geoService.getCurrentLatLon(),
          // );

          this.headerComponent.fetchSuggestions('');
        } finally {
          this.loading = false;
        }
        this.changeRef.detectChanges();
      }),
    );
  }

  formatDate(date: Dayjs, format: string) {
    return date?.locale('de')?.format(format);
  }

  private getPageTags(): PageTags {
    const location = this.location;
    if (location) {
      return {
        title: `Events in ${location.displayName}, ${location.area} | Entdecke lokale Veranstaltungen, Familien- und Sport-Aktivitäten in deiner Umgebung`,
        description: `Erfahre alles über aktuelle Veranstaltungen in ${location.displayName}, ${location.area}. Von Veranstaltungen, Familien- und Sport-Aktivitäten und Märkten, entdecke, was in ${location.displayName}, ${location.area} geboten wird. Ideal für Einheimische, Familien und Besucher.`,
        publisher: 'upcoming',
        category: '',
        url: document.location.href,
        region: location.area,
        place: location.displayName,
        lang: 'de',
        publishedAt: dayjs(),
        position: [location.lat, location.lon],
      };
    } else {
      return {
        title: `lokale.events | Entdecke lokale Veranstaltungen, Familien- und Sport-Aktivitäten in deiner Umgebung`,
        description: `Erfahre alles über aktuelle Veranstaltungen in deiner Umgebung. Von Veranstaltungen, Familien- und Sport-Aktivitäten und Märkten, entdecke. Ideal für Einheimische, Familien und Besucher.`,
        publisher: 'upcoming',
        category: '',
        url: document.location.href,
        // region: location.area,
        // place: location.displayName,
        lang: 'de',
        publishedAt: dayjs(),
        // position: [location.lat, location.lon],
      };
    }
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach((s) => s.unsubscribe());
  }

  private getDistanceFromLatLonInKm(
    lat1: number,
    lon1: number,
    lat2: number,
    lon2: number,
  ) {
    const R = 6371; // Radius of the earth in km
    const dLat = this.deg2rad(lat2 - lat1); // deg2rad below
    const dLon = this.deg2rad(lon2 - lon1);
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos(this.deg2rad(lat1)) *
        Math.cos(this.deg2rad(lat2)) *
        Math.sin(dLon / 2) *
        Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    return R * c; // Distance in km
  }

  private deg2rad(deg: number) {
    return deg * (Math.PI / 180);
  }

  private fetchEventOfDay(day: Dayjs): Promise<Record[]> {
    return this.recordService.findAllByRepositoryId({
      cursor: {
        page: 0,
        pageSize: 50,
      },
      where: {
        repository: {
          id: this.getRepositoryId(),
        },
        latLng: {
          near: {
            lat: this.latLon[0],
            lon: this.latLon[1],
          },
          distanceKm: this.perimeter,
        },
        startedAt: {
          after: day
            .clone()
            .set('hours', 0)
            .set('minutes', 0)
            .set('seconds', 0)
            .valueOf(),
          before: day
            .clone()
            .set('hours', 24)
            .set('minutes', 0)
            .set('seconds', 0)
            .valueOf(),
        },
      },
    });
  }

  private async fetchEvents(date: Dayjs) {
    try {
      this.loadingDay = true;
      this.placesByDistance = [];
      this.changeRef.detectChanges();

      const events = await this.fetchEventOfDay(date.clone());
      this.eventCount = events.length;

      const places: NamedLatLon[] = await Promise.all(
        unionBy(
          events.map((e) => e.latLng),
          (e) => `${e.lat},${e.lon}`,
        )
          .filter((e) => e)
          .map((latLon) => {
            const namedPlace = getCachedLocations().find(
              (place) =>
                roundLatLon(place.lat) == roundLatLon(latLon.lat) &&
                roundLatLon(place.lon) == roundLatLon(latLon.lon),
            );
            if (namedPlace) {
              return namedPlace;
            } else {
              console.log('Cannot resolve', latLon);
              return this.openStreetMapService.reverseSearch(
                latLon.lat,
                latLon.lon,
              );
            }
          }),
      );

      const groups = events.reduce((agg, event) => {
        const distance = this.getGeoDistance(event).toFixed(0);
        if (!agg[distance]) {
          agg[distance] = [];
        }

        agg[distance].push(event);

        return agg;
      }, {} as Distance2Events);

      this.placesByDistance = sortBy(
        Object.keys(groups).map((distance) => ({
          distance,
          place: null,
          events: groups[distance],
        })),
        (event) => parseInt(event.distance),
      ).reduce((groupedPlaces, eventGroup: EventsByDistance) => {
        const latLonGroups = groupBy(eventGroup.events, (e) =>
          JSON.stringify(e.latLng),
        );
        groupedPlaces.push({
          distance: parseInt(eventGroup.distance),
          places: Object.keys(latLonGroups).map((latLonGroup) => {
            const latLon = latLonGroups[latLonGroup][0].latLng;
            const place = places.find(
              (place) =>
                roundLatLon(place.lat) == roundLatLon(latLon.lat) &&
                roundLatLon(place.lon) == roundLatLon(latLon.lon),
            );
            if (!place) {
              throw new Error(`Cannot resolve latlon` + latLon);
            }
            return {
              events: latLonGroups[latLonGroup],
              place: place,
            };
          }),
        });

        return groupedPlaces;
      }, [] as PlaceByDistance[]);

      this.pageService.setJsonLdData(this.createWebsiteSchema());
    } catch (e) {
      console.error(e);
    } finally {
      this.loadingDay = false;
    }
    this.changeRef.detectChanges();
  }

  private getGeoDistance(event: Record): number {
    return this.getDistanceFromLatLonInKm(
      event.latLng.lat,
      event.latLng.lon,
      this.latLon[0],
      this.latLon[1],
    );
  }

  private getRepositoryId(): string {
    return this.appConfigService.customProperties.eventRepositoryId as any;
  }

  createWebsiteSchema(): WebPage {
    const tags = this.getPageTags();
    return {
      '@type': 'WebPage',
      name: tags.title,
      description: tags.description,
      datePublished: tags.publishedAt.toISOString(),
      url: location.href,
      breadcrumb: createBreadcrumbsSchema(this.location),
      mainEntity: {
        '@type': 'ItemList',
        itemListElement: this.placesByDistance
          .flatMap((distanced) => distanced.places)
          .filter((place) => place.place)
          .map((place) => this.toSchemaOrgPlace(place)),
      },
    };
  }

  private toSchemaOrgEvent(event: Record): SchemaEvent {
    return {
      '@type': 'Event',
      name: event.title,
      description: event.text,
      startDate: dayjs(event.startingAt).format('YYYY-MM-DD'),
      // endDate: '2024-11-15T22:15',
      url: event.url,
      location: {
        '@type': 'Place',
        name: 'Hedingen',
      },
    };
  }

  getEventUrl(event: Record) {
    const { countryCode, region, place, year, month, day } =
      this.activatedRoute.snapshot.params;

    return (
      '/' +
      homeRoute({})
        .events({})
        .countryCode({ countryCode })
        .region({ region })
        .place({ place })
        .dateTime({
          perimeter: this.perimeter,
          year,
          month,
          day,
        })
        .eventId({ eventId: event.id }).$
    );
  }

  private toSchemaOrgPlace(place: EventsAtPlace): SchemaPlace {
    return {
      '@type': 'Place',
      name: place.place.place,
      geo: {
        '@type': 'GeoCoordinates',
        latitude: place.place.lat,
        longitude: place.place.lon,
      },
      event: place.events.map((event) => this.toSchemaOrgEvent(event)),
    };
  }

  getPlaceUrl(location: NamedLatLon): string {
    if (location) {
      const { countryCode, area, place } = location;
      const { year, month, day } =
        homeRoute.children.events.children.countryCode.children.region.children.place.children.dateTime.parseParams(
          this.activatedRoute.snapshot.params as any,
        );
      return (
        '/' +
        homeRoute({})
          .events({})
          .countryCode({ countryCode })
          .region({ region: area })
          .place({ place })
          .dateTime({
            perimeter: this.perimeter,
            year,
            month,
            day,
          }).$
      );
    }
  }

  async changeDate(date: Dayjs) {
    this.date = date;
    this.createDateWindow(this.date);
    this.patchUrlInAddressBar();
    await this.fetchEvents(this.date);
  }

  patchUrlInAddressBar() {
    const url = homeRoute({})
      .events({})
      .countryCode({ countryCode: this.location.countryCode })
      .region({ region: this.location.area })
      .place({ place: this.location.place })
      .dateTime({
        perimeter: 10,
        year: parseInt(this.date.format('YYYY')),
        month: parseInt(this.date.format('MM')),
        day: parseInt(this.date.format('DD')),
      }).$;

    this.locationService.replaceState(url);
  }

  private saveLocation(location: Nullable<NamedLatLon>) {
    const savedLocations: NamedLatLon[] = JSON.parse(
      localStorage.getItem('savedLocations') || '[]',
    );
    const locations = uniqBy(
      [location, ...savedLocations],
      (l) => `${l.lat}:${l.lon}`,
    ).filter((_, index) => index < 5);
    localStorage.setItem('savedLocations', JSON.stringify(locations));
  }

  private createDateWindow(date: Dayjs) {
    // if (!this.dateWindow.some(otherDate => this.isSame(otherDate, date, ['date', 'month', 'year']))) {
    this.dateWindow = [
      date.subtract(1, 'day'),
      date,
      date.add(1, 'day'),
      date.add(2, 'day'),
      date.add(3, 'day'),
      date.add(4, 'day'),
      date.add(5, 'day'),
    ];
    this.changeRef.detectChanges();
    // }
  }

  isToday(day: Dayjs): boolean {
    return this.isSame(day, dayjs(), ['day', 'month', 'year']);
  }

  isPast(day: Dayjs): boolean {
    return day.isBefore(
      dayjs()
        .set('hours', 0)
        .set('minutes', 0)
        .set('seconds', 0)
        .set('milliseconds', 0),
    );
  }

  getWeekday(date: Dayjs): string {
    if (date) {
      const now = dayjs()
        .set('hours', date.hour())
        .set('minutes', date.minute())
        .set('seconds', date.second())
        .set('milliseconds', date.millisecond());
      const diffInHours = date.diff(now, 'day');
      switch (diffInHours) {
        case 0:
          return 'Heute';
        case -1:
          return 'Gestern';
        case 1:
          return 'Morgen';
        default:
          return ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'][date.day()];
      }
    }
  }

  protected isSame(a: dayjs.Dayjs, b: dayjs.Dayjs, units: dayjs.OpUnitType[]) {
    return units.every((unit) => a.isSame(b, unit));
  }

  moveCalendarWindow(days: number) {
    this.createDateWindow(this.dateWindow[0].add(days, 'days'));
  }

  isDateInCalendar(date: Dayjs) {
    return this.dateWindow.some((d) =>
      this.isSame(date, d, ['year', 'month', 'day']),
    );
  }
}

export function getSavedLocations(): NamedLatLon[] {
  return JSON.parse(localStorage.getItem('savedLocations') || '[]');
}
