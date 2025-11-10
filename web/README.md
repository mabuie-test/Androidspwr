# Web Platform

This folder groups the projects that power the Device Manager Suite web
experience. It contains the REST API used by the mobile app and dashboards, and
all static assets served to end users.

## Structure

- `backend/` – Node.js (Express) API responsible for authentication, device
  management, telemetry, media handling, and payment endpoints.
- `web-frontend/` – Static HTML/CSS/JS assets for the admin and user portals.

## Backend setup

1. Navigate to the backend folder:
   ```bash
   cd web/backend
   ```
2. Install dependencies:
   ```bash
   npm install
   ```
3. Copy the environment example and adjust the values for your environment:
   ```bash
   cp .env.exemplo.js .env
   ```
4. Start the development server:
   ```bash
   npm start
   ```

## Frontend assets

The contents of `web/web-frontend` are static files. You can serve them from any
static file server or include them in the backend's public directory, depending
on your deployment strategy.
