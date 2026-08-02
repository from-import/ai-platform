# AI Platform Frontend

The web application is a React single-page application. It is developed separately from the Spring Boot backend, but it can still be shipped inside the same Spring Boot application.

## Source Layout

```text
src/
├── api/          Backend request functions and TypeScript API contracts
├── components/   Shared visual components
├── hooks/        Small reusable React behaviors
├── pages/        Playground, Analytics and Request Logs pages
├── App.tsx       Routes and application-level data
├── main.tsx      React entry point
└── styles.css    Shared application styles
```

The frontend uses `HashRouter`, so routes look like `/#/analytics`. The part after `#` stays in the browser and does not require a Spring MVC fallback controller.

## First-time Setup

Install Node.js 20.19 or newer and pnpm 11, then install the dependencies:

```bash
cd frontend
pnpm install
```

## Development

Start Spring Boot from IntelliJ IDEA on port `8080`. In another terminal, run:

```bash
cd frontend
pnpm dev
```

Open `http://localhost:5173`. Vite provides hot reload and proxies `/api` requests to `http://localhost:8080`, so no CORS configuration is needed.

## Build into Spring Boot

Run this command whenever the frontend should be served by Spring Boot:

```bash
cd frontend
pnpm build:spring
```

The command type-checks the React source, creates optimized JavaScript and CSS, and replaces the generated files in:

```text
../src/main/resources/static/
```

After that, start the backend from IntelliJ IDEA and open `http://localhost:8080`. Do not edit the generated `static/index.html` or `static/assets/*` files directly; change files under `frontend/src` and rebuild instead.

For a runnable production JAR, build the frontend first and then package Spring Boot:

```bash
cd frontend
pnpm build:spring
cd ..
mvn clean package
java -jar target/ai-platform-0.0.1-SNAPSHOT.jar
```

## Useful Commands

```bash
pnpm typecheck     # Check TypeScript without producing files
pnpm build         # Build only to frontend/dist
pnpm build:spring  # Build into Spring Boot static resources
```
