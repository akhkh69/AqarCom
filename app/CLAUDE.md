# AqarCom (عقاركم) — project context for Claude Code

Graduation project (engineering informatics). Syrian real-estate platform:
- **B2C**: property listings (sale / rent / investment), search, interactive map, favorites, contact owner.
- **B2B**: tenders posted by project owners, bids from verified contractors, automatic ranking with the
  Weighted Sum Model (WSM), shortlist of top 5, award.
- Reference documents: SRS v1.1 (functional requirements FR-01 … FR-28, business rules BR-01 … BR-20,
  test cases TC-01 … TC-14). Code comments cite these ids, e.g. `/** FR-12 … */` — search for them.

Owner: Ali Khaiti. Team on Jira project KAN (AkarCom). Answer in Arabic (Syrian dialect) unless asked otherwise.

## Repositories / folders
- `aqarcom-backend/server` — Node.js + Express + Mongoose (MongoDB). The only part required to run the app.
- `aqarcom-backend/server/admin-dashboard` — admin web dashboard (vanilla HTML/CSS/JS ES modules), served at `/dashboard`.
- `aqarcom-backend/scoring-service` — optional FastAPI + NumPy/Pandas WSM service. If it is down the server uses a JS FallbackEngine (same formula).
- `aqarcom-backend/deploy`, `docker-compose.yml` — production only (nginx TLS 1.3). `android-client/` is obsolete.
- `AqarComApp` — Android app (Java).

## Backend
- Run: `cd server && npm install && npm run seed && npm run dev` → http://localhost:3000 (API under `/api/v1`, Swagger at `/docs`, dashboard at `/dashboard`).
- Needs a real MongoDB (map uses `$geoWithin $box`). Config in `server/.env` (copy `.env.example`); `MONGO_URI=mongodb://127.0.0.1:27017/aqarcom`.
- Auth: JWT HS256 access token (1 h) + rotating single-use refresh token (30 d); bcrypt 12 rounds; Joi validation; failed-login lockout (FR-04).
- Accounts: status `pending | active | rejected | suspended`. Privileged roles (property_owner, project_owner, contractor) start `pending`.
  **Approval / role changes happen ONLY from the admin dashboard** (`POST /admin/users/:id/approve|reject|suspend|reactivate|role`).
- Files: AES-256-GCM encrypted store; resumable chunked uploads (`/uploads`), spec book ≤ 20 MB PDF/Word, versions kept (FR-15).
- Tenders: node-cron + lazy close, atomic state transitions, server time is authoritative (BR-11); extend once (FR-14).
- WSM: r = min/x per criterion; S = wp·rp + wd·rd; tie → lower price, then earlier submission (BR-19). Result stores engine name (wsm | fallback).
- Response envelope: `{ success, data, meta }` or `{ success:false, error:{ code, message, details } }`.
- Tests: `server/tests/api.test.js` (34 tests).
- Demo accounts (password `Passw0rd!`): admin@, user@, owner@, project@, contractor1..3@ (verified), contractor4@ (pending),
  pending.owner@, pending.project@, rejected@ — all `@aqarcom.app`.

## Android app (AqarComApp)
- Java, AGP 8.5.2, Gradle 8.7, compileSdk 34, minSdk 24, ViewBinding, Material3, Retrofit 2.11 / OkHttp 4.12 / Gson, Glide 4.16,
  osmdroid 6.1.20 (OpenStreetMap — no API key), play-services-location.
- Forced Arabic RTL (`ui/base/LocaleHelper`). Palette #123A5E / #B8873B / #2A7D62 / #F7F3EC.
- Server URL: `API_BASE_URL` in `local.properties` (default `http://10.0.2.2:3000/api/v1/` for the emulator; a phone needs the PC LAN IP).
- `gradle.properties` has `android.overridePathCheck=true`; keep the project in an ASCII-only path.
- Package layout `com.aqarcom.app`:
  - `data/api` — Retrofit interfaces (Auth, Property, Tender, Bid, Notification, Verification, Upload, Admin — Admin is unused by the app on purpose).
  - `data/model` — DTOs matching the server JSON field names exactly (Gson).
  - `data/network` — `ApiClient` (single Retrofit), `AuthInterceptor` (Bearer token), `TokenAuthenticator` (refresh on 401),
    `TokenManager`, `ApiCallback` (unwraps envelope → onSuccess/onError), `ErrorParser`.
  - `data/upload/ChunkedUploader` — files > 5 MB.
  - `ui/{auth,main,property,tender,account,common,base}` — screens. Every screen: ViewBinding → `ApiClient.x().y().enqueue(ApiCallback)` → `bind()`.
- Map: `ui/main/MapFragment` (colored pins by purpose, preview card, my location), `ui/property/LocationPickerActivity`,
  mini map in `PropertyDetailActivity`.

## Requirements traceability (short)
FR-01..04 auth (`ui/auth/*`, `data/network/*`) · FR-05..09 properties/search/map/favorites (`ui/property/*`, `ui/main/Home|Map|Favorites`) ·
FR-10..16 tenders (`ui/tender/*`) · FR-17,19 server-side scoring · FR-18,20 `ShortlistActivity` · FR-21,26 `NotificationsFragment` ·
FR-22,23 `VerificationActivity`, `TenderDetailActivity.bindContractor()` · FR-24,27,28 admin dashboard (`js/pages/verifications|content|overview.js`) ·
FR-25 `PropertyDetailActivity.contact()`.

## Conventions
- Keep FR/BR ids in comments when adding features.
- Don't move admin actions into the Android app.
- Verify Android changes compile (Gradle sync) and backend changes with `npm test`.
