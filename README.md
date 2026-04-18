# devdevgo jobs-service

Reactive Spring Boot microservice that fetches jobs from Adzuna and stores them in Firebase Firestore when enabled.

## Environment variables
- `ADZUNA_APP_ID`
- `ADZUNA_APP_KEY`
- `FIREBASE_ENABLED=true|false`
- `FIREBASE_CREDENTIALS_PATH=/path/to/service-account.json`
- `FIREBASE_PROJECT_ID=your-project-id`
- `JOBS_SYNC_ENABLED=true|false`
- `JOBS_SYNC_BATCH_SIZE=2`

## Endpoints
- `GET /api/v1/jobs/ping`
- `GET /api/v1/jobs/search?q=flutter intern&location=in&page=1&size=10`
- `POST /api/v1/jobs/sync?batchSize=2`
