import { buildApp } from './app';
import { config } from './config';

buildApp()
  .listen({ port: config.port, host: config.host })
  .catch((err) => {
    console.error(err);
    process.exit(1);
  });
