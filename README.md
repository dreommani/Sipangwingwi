# Sipangwingwi

Kotlin Multiplatform project with a shared Compose UI, shared models, and a Ktor backend.

## Structure

- `composeApp` - Compose Multiplatform app for Android, iOS, JS, and Wasm.
- `shared` - common Kotlin code used by both the app and backend.
- `server` - JVM backend built with Ktor.
- `iosApp` - iOS app entry point that hosts the shared Compose UI.

## Backend

The backend starts from:

```text
server/src/main/kotlin/com/example/sipangwingwi/Application.kt
```

It runs a Ktor Netty server on port `8081` and exposes:

- `GET /` - basic greeting response.
- `GET /health` - health check endpoint.
- `GET /users` - returns users from the database as JSON.

JSON serialization is handled by Ktor Content Negotiation with kotlinx.serialization.

## Database

Database setup is in:

```text
server/src/main/kotlin/com/example/sipangwingwi/Database.kt
```

The backend uses:

- PostgreSQL
- HikariCP for connection pooling
- Exposed ORM for table definitions and queries

Current local database config:

```text
Database: family_tree
Host: localhost
Port: 5432
User: postgres
Password: postgres
```

The backend creates a `users` table if it does not already exist.

## Shared Code

The `shared` module contains common models and constants.

Important shared files:

- `models/User.kt` - serializable user model shared by app and backend.
- `Constants.kt` - shared server port constant.
- `Greeting.kt` - common greeting logic.

## App

The shared Compose UI lives in:

```text
composeApp/src/commonMain/kotlin/com/example/sipangwingwi/App.kt
```

Platform entry points call the same `App()` function for Android, iOS, and Web.

An API client exists at:

```text
composeApp/src/commonMain/kotlin/com/example/sipangwingwi/ApiClient.kt
```

It can call the backend `/users` endpoint, but the current UI does not display users yet.

## Run

Run the backend:

```powershell
.\gradlew.bat :server:run
```

Build Android:

```powershell
.\gradlew.bat :composeApp:assembleDebug
```

Run Web Wasm:

```powershell
.\gradlew.bat :composeApp:wasmJsBrowserDevelopmentRun
```

Run iOS:

Open the `iosApp` folder in Xcode and run the app from there. The iOS app uses the shared Compose UI from `composeApp`.

## Keep Backend Running On Windows

Yes, the backend can run as a background process on your PC.

For development, run it manually:

```powershell
.\gradlew.bat :server:run
```

For an always-running local setup on Windows, the best approach is usually to package the backend and run it as a Windows service using a tool like NSSM, or create a Windows Task Scheduler task that starts the backend when you log in.

Before doing that, build the backend:

```powershell
.\gradlew.bat :server:installDist
```

Then the generated server start script will be in:

```text
server/build/install/server/bin/server.bat
```

That script can be used by Task Scheduler or a Windows service manager.

## Notes

This is currently a development setup. Database credentials are hardcoded and should be moved to environment variables or config before production use.
