# DevDevGo Jobs Service — Frontend API Doc

## Status snapshot

The backend sync flow now:
- loads sync state,
- checks the minimum sync interval,
- rotates through search profiles,
- fetches Adzuna jobs,
- deduplicates by `title + company + location`,
- stores data,
- purges jobs older than 7 days using `createdAt`,
- and persists sync success/failure state. fileciteturn1file0

This document is for frontend integration only.

---

## Base URL

`/api/v1/jobs`

All endpoints return JSON.

---

## What the service does

The service is a tech-job backend that:
- pulls jobs from Adzuna,
- keeps only tech/fresher/intern-oriented search profiles,
- stores jobs in Firebase and/or Elasticsearch depending on configuration,
- keeps a rolling 7-day data window,
- and exposes a search-friendly API for the frontend.

---

## Public endpoints

### 1) Health check

`GET /api/v1/jobs/ping`

Purpose:
- quick uptime check
- useful for debugging and deployment verification

Response shape:
```json
{
  "timestamp": "2026-04-18T12:00:00Z",
  "status": 200,
  "message": "devdevgo jobs service is running",
  "path": "/api/v1/jobs/ping"
}
```

---

### 2) Recent jobs

`GET /api/v1/jobs?page=1&size=20`

Purpose:
- fetch the latest jobs without filters
- frontend home feed / default list

Query params:
- `page` — 1-based page number
- `size` — page size

Behavior:
- sorted by newest fetched jobs first
- paginated on the backend

Response shape:
```json
{
  "total": 1234,
  "page": 1,
  "size": 20,
  "items": [ ...jobs... ]
}
```

---

### 3) Search + filters

`GET /api/v1/jobs/search?q=flutter intern&location=bangalore&tags=flutter&tags=intern&page=1&size=10`

Purpose:
- frontend search bar
- filter chips
- location refinement
- tag refinement

Supported query params:
- `q` — free-text search
- `location` — location filter
- `tag` — repeated parameter form
- `tags` — repeated parameter form
- `page` — 1-based page number
- `size` — page size

Tag behavior:
- `tag` and `tags` are merged
- duplicates are removed
- multiple tags are treated as **AND**
- search text tokens are also treated as **AND**-style matching

Response shape:
```json
{
  "total": 87,
  "page": 1,
  "size": 10,
  "items": [ ...jobs... ]
}
```

---

### 4) Sync trigger

`POST /api/v1/jobs/sync?batchSize=8&force=false`

Purpose:
- internal/manual sync
- admin/testing only
- not needed for normal frontend browsing

Query params:
- `batchSize` — requested number of search profiles to sync
- `force` — bypasses the minimum sync interval

Important runtime note:
- the server caps the effective batch size by config
- so the requested value may be lower than the value actually used

Response shape:
```json
{
  "firebaseEnabled": true,
  "queriesRun": 2,
  "jobsFetched": 20,
  "jobsSaved": 18,
  "profiles": ["software-fresher", "software-intern"],
  "startedAt": "2026-04-18T12:00:00Z",
  "finishedAt": "2026-04-18T12:00:07Z",
  "note": "Synced jobs into Firestore"
}
```

---

## Job item fields returned to the frontend

Each item in `items` is a job record with these fields:

- `id`
- `title`
- `company`
- `location`
- `description`
- `redirectUrl`
- `createdAt`
- `contractType`
- `contractTime`
- `salaryMin`
- `salaryMax`
- `salaryPredicted`
- `latitude`
- `longitude`
- `category`
- `categoryTag`
- `source`
- `searchProfile`
- `searchedWhat`
- `searchedWhere`
- `fetchedAt`
- `normalizedText`
- `tags`

Recommended frontend use:
- `title`, `company`, `location` for cards
- `tags` for filter chips and badges
- `redirectUrl` for apply button
- `createdAt` for freshness label
- `salaryMin` / `salaryMax` for salary display when present

---

## Filtering rules the frontend should expect

### Search text (`q`)
- matches against title/company/location/description/category/tags and related fields
- token matching is strict enough to prefer relevant results

### Location
- best used as a direct search filter
- use a single value like `bangalore`, `remote`, `hyderabad`

### Tags
- multiple tags mean the job must contain **all** selected tags
- examples:
  - `intern`
  - `flutter`
  - `backend`
  - `data-science`
  - `machine-learning`

### Sorting
- results are newest-first by backend fetch time

---

## Recommended frontend flows

### Home feed
1. call `GET /api/v1/jobs?page=1&size=20`
2. render cards
3. load more with next page

### Search flow
1. user types query
2. call `GET /api/v1/jobs/search`
3. pass selected filter chips as repeated `tags`
4. paginate from the backend

### Job details
1. open the `redirectUrl`
2. optionally display the job card fields in a detail modal/page first

---

## Error responses

The API returns JSON errors with:
- `timestamp`
- `status`
- `error`
- `message`
- `path`

Common statuses:
- `400` bad request
- `502` Adzuna upstream error
- `504` timeout
- `500` unexpected server error

Frontend recommendation:
- show a friendly error banner
- keep the current results visible when a refresh fails
- retry search once for transient network issues

---

## System behavior the frontend should know

### Sync cadence
- the backend auto-syncs on startup and on a fixed interval
- it skips sync when the last sync is too recent
- it rotates through search profiles instead of hitting all of them every cycle

### Retention
- old jobs are automatically purged after 7 days
- the feed is a rolling window, not a permanent archive

### Data source behavior
- Firebase is the durable store when enabled
- Elasticsearch is used for search when enabled
- if search is unavailable, the backend still falls back to the store layer

### Duplicate handling
- the backend deduplicates jobs before storage
- duplicates may still be fetched from Adzuna, but they should not be shown multiple times in storage/search

---

## Frontend integration notes

- treat `page` as 1-based
- do not assume `items.length == size`
- always render from `items`
- use `total` for pagination controls
- display empty states when `items` is empty
- keep `tags` as a multi-select filter
- keep search and filters as backend-driven, not frontend-only

---

## Current configuration notes

For production readiness, the backend must have:
- valid Adzuna credentials
- Firebase enabled if you want sync state persistence
- Elasticsearch enabled if you want backend search indexing
- environment-based secrets instead of hardcoded keys
- correct Render environment variables

---

## Frontend contract summary

### Read-only endpoints the app should use
- `GET /api/v1/jobs`
- `GET /api/v1/jobs/search`
- `GET /api/v1/jobs/ping`

### Admin/internal endpoint
- `POST /api/v1/jobs/sync`

---

## Example UI mapping

- Home tab → `GET /api/v1/jobs`
- Search tab → `GET /api/v1/jobs/search`
- Filter chips → `tags`
- Location dropdown → `location`
- Pull-to-refresh → repeat current search request
- Apply button → `redirectUrl`

