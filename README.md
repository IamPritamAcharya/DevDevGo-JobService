# Jobs Service

Production-focused job ingestion and search service for DevDevGo.

## What it does

- Fetches tech-only jobs from Adzuna
- Rotates through fresher and intern-focused search profiles
- Stores listings in Firestore when enabled
- Indexes listings in Elasticsearch when enabled
- Keeps a persistent sync state in Firebase metadata so restarts do not reset rotation
- Deletes listings older than 7 days using the Adzuna `created` timestamp
- Prevents syncs from running more than once per minimum interval

## Search and filtering

Search is handled on the backend, not in the frontend.

Supported filters:
- query text (`q`)
- location
- tags (`tag` or `tags`)

## Important runtime notes

- Elasticsearch is optional. Enable with `JOBS_ELASTICSEARCH_ENABLED=true` + `ELASTICSEARCH_URIS`.
- Render free services spin down after inactivity. On next startup the service loads Firebase sync state and only runs if the 1-hour minimum interval has passed — no duplicate API calls.
- Sync state (rotationIndex + lastSyncTime) is stored in Firebase `system_metadata/job_sync_state` so rotation resumes from where it left off after any restart.
- Cleanup runs every sync cycle and deletes jobs where Adzuna's `created` date is older than 7 days.
- A global `AtomicBoolean` guard prevents two syncs from running at the same time.

## Key endpoints

- `GET /api/v1/jobs/ping`
- `GET /api/v1/jobs?page=1&size=20`
- `GET /api/v1/jobs/search?q=flutter+intern&tags=intern&location=bangalore`
- `POST /api/v1/jobs/sync?batchSize=8&force=false`

## Environment variables

### Adzuna
- `ADZUNA_APP_ID`
- `ADZUNA_APP_KEY`

### Firebase / Firestore
- `FIREBASE_ENABLED` (default `true`)
- `FIREBASE_CREDENTIALS_PATH`
- `FIREBASE_PROJECT_ID`
- `FIREBASE_COLLECTION_NAME` (default `job_listings`)
- `FIREBASE_METADATA_COLLECTION_NAME` (default `system_metadata`)

### Elasticsearch
- `JOBS_ELASTICSEARCH_ENABLED` (default `false`)
- `ELASTICSEARCH_URIS` (default `http://localhost:9200`)
- `ELASTICSEARCH_USERNAME`
- `ELASTICSEARCH_PASSWORD`

### Sync tuning
- `JOBS_SYNC_ENABLED` (default `true`)
- `JOBS_SYNC_INTERVAL` (default `PT1H`)
- `JOBS_SYNC_MIN_INTERVAL` (default `PT1H`)
- `JOBS_SYNC_BATCH_SIZE` (default `8`)
- `JOBS_SYNC_PURGE_AFTER_DAYS` (default `7`)

## API usage budget

```
8 profiles/cycle × 8 results/profile = 8 API calls/hour
→ 192 API calls/day  →  ~1,500 fresh jobs/day (rolling 7-day window)
```
