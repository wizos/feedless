import { AppEnvironment } from '../app/app.module';

export const environment: AppEnvironment = {
  production: false,
  product: () => 'scrape-api',
};
