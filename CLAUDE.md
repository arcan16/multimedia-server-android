# CLAUDE.md — Android

This file provides guidance to Claude Code (claude.ai/code) when working in `Android/`.

## Scope

This folder is the MultimediaServer Android client: a native app in **Java** (no Kotlin, no Compose) that replicates the web client in `../Frontend/` screen by screen. It talks to the backend only over HTTP. **Do not edit `../Backend/` or `../Frontend/` from an Android session.**

- **The API contract lives in `../Frontend/CLAUDE.md`** ("API contract" section). Read it before touching any call; it is the single source of truth for endpoints, shapes, status codes, response quirks and roles. If the app needs an API change, describe it to the user as a backend task.
- When the web client changes a screen or a rule, mirror it here; the React file each Android screen replicates is named in its class Javadoc.

Status: **scaffolded**. All 10 screens are built and the project compiles (`assembleDebug` and `lintDebug` pass). It has not yet been run on a device or emulator.

```bash
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # on a connected device/emulator
./gradlew lintDebug
./gradlew assembleDebug -PapiUrl=http://192.168.1.10:8080   # change the default server URL
```

Toolchain: JDK 17, Gradle 9.6 (wrapper), AGP 9.4.1, `compileSdk`/`targetSdk` 37, `minSdk` 24. Libraries: AppCompat, Material Components (dark theme), ConstraintLayout, RecyclerView, SwipeRefreshLayout, OkHttp 4, Gson. ViewBinding is on. No image or video libraries: posters use `PosterLoader` and playback uses the platform `VideoView`.

## Project structure

Package `com.multimediaserver.android` (`app/src/main/java/...`):

```
MultimediaApp            Application: initializes ServerConfig + Session, ends the session on 401
api/
  ApiClient              the only code that talks HTTP; every response quirk lives here
  Api                    one nested class per resource (Auth, Users, Genres, MoviesSeries, Media)
  ApiError               status + user-facing Spanish message + fieldErrors (validation list by field)
  Session                the only owner of the JWT (see "Session and credentials")
  ServerConfig           backend base URL (BuildConfig.API_URL, editable on the login screen)
  MultipartForm          multipart builder (repeats list values, skips empty ones), like toFormData
  UriRequestBody         streams a picked content:// file and reports upload progress
model/                   Gson DTOs: User, Genre, MovieSeries, Multimedia, LoginResponse
util/
  Async                  runs blocking calls off the main thread; delivers only while the owner is alive
  Format                 port of Frontend/src/utils/format.js
  GenreCache             shared genre list (useGenres)
  PosterLoader           authorized poster fetch + LruCache (usePosterUrl)
  PickedFile             name/MIME/size of a file picked with the document picker
ui/
  MainActivity           shell: navigation drawer (Sidebar) hosting the drawer destinations as fragments
  common/                SessionActivity (RequireAuth + expiry timer), Ui (alerts, states, pagination,
                         confirm dialogs, form helpers), PosterView, Dropdown
  auth/LoginActivity     launcher; restores a remembered session with GET /users/me
  catalog/               CatalogFragment, TitleDetailActivity, TitleCardAdapter
  player/PlayerActivity
  account/AccountFragment
  admin/                 ContentFragment, MovieSeriesFormActivity, DeleteTitleDialog,
                         MultimediaFragment, GenresFragment, UsersFragment, UserFormDialog
```

- **Navigation**: drawer destinations (catalog, account and the four admin screens) are fragments in `MainActivity`; detail, player and the title form are separate activities. Other screens open a drawer destination with `MainActivity.intent(context, destination, titleId, editId)`.
- **Data loading**: `Async.run(lifecycleOwner, work, onSuccess, onError)`. In fragments pass `getViewLifecycleOwner()`. Screens reload in `onResume` so they reflect changes made in other screens. A request counter drops stale responses in the searchable lists.
- **Errors**: every failure is an `ApiError`; show `getMessage()` as-is. Map `fieldErrors` to the `TextInputLayout` of each field.
- **System bars**: with `targetSdk` 35+ Android draws edge to edge. Every activity calls `SystemBars.fit(this)` right after `setContentView`; it pads the content for the status bar, navigation bar, cutouts and keyboard. New activities must do the same.
- **Styling**: tokens in `res/values/colors.xml` (same values as the web `tokens.css`); text, button and input styles in `res/values/styles.xml`; shapes in `res/drawable/bg_*.xml`. Headings use `sans-serif-medium` bold (Sora and Inter are not bundled).
- **UI copy** is Spanish and written inline in layouts and code, as in the web client (lint reports it as `HardcodedText`; that is expected).

## Screens (same routes and roles as the web)

| Web route | Android | Roles |
|---|---|---|
| `/login` | `LoginActivity` (+ "Servidor" field for the backend URL) | public |
| `/` | `CatalogFragment`: name search (300 ms debounce), type toggle, genre chips, poster grid, pagination | USER, ADMIN |
| `/titles/:id` | `TitleDetailActivity`: season tabs, episode rows, admin edit/upload/delete | USER, ADMIN |
| `/watch/:multimediaId` | `PlayerActivity`: `VideoView` + "Siguientes episodios"; landscape is full screen; autoplays the next episode | USER, ADMIN |
| `/account` | `AccountFragment` | USER, ADMIN |
| `/admin/content` | `ContentFragment` (+ `DeleteTitleDialog` cascade confirmation) | ADMIN |
| `/admin/content/new`, `/:id/edit` | `MovieSeriesFormActivity` (poster picked with the system document picker) | ADMIN |
| `/admin/multimedia` | `MultimediaFragment` (upload with progress and cancel) | ADMIN |
| `/admin/genres` | `GenresFragment` (inline edit) | ADMIN |
| `/admin/users` | `UsersFragment` + `UserFormDialog`; another admin's row is locked | ADMIN |

- The "Administración" drawer section and the create/edit/delete buttons are shown only to ADMIN; `MainActivity.navigate` also sends a USER away from admin destinations. **The backend is the authority**: 403 is shown as any other error.

## Implementation decisions (departures from the web client)

- **Catalog and content filters are server-side**: the app sends `type` and `genreId` to `GET /movies-series` (the contract supports them), so filtering is exact across pages. The web client still filters the current page.
- **No `/users/me` fallback**: the login response already carries `id` and `rol`, and a resumed session calls `GET /users/me` (in the contract).
- **Server URL**: the backend is self-hosted, so the login screen lets the user change its address. The default is `BuildConfig.API_URL` (`http://10.0.2.2:8080`, the host machine from the emulator). Cleartext HTTP is allowed (`res/xml/network_security_config.xml`) because the server usually runs on the local network.
- **Player** is the platform `VideoView` + `MediaController` (no ExoPlayer/Media3), with the URL `/multimedia/{id}/video?token=<jwt>` built when playback starts.
- **Uploads** use OkHttp with no write timeout and a 30-minute read timeout (the server runs ffprobe before answering). Leaving the "Episodios y vídeo" screen cancels a running upload, like unmounting the web form.
- **Drag and drop** in the web dropzones becomes a tap that opens the system file picker.

## Session and credentials

- **The password is never stored.** It lives only in the login form and is cleared after a failed attempt.
- **Only the JWT and the username are kept**, by `api/Session`. Nothing else reads or writes the token.
  - The token is held in memory. When the user ticks "Mantener la sesión iniciada" it is also persisted in private `SharedPreferences` (`ms.session`), so it survives the app being closed; otherwise the session ends with the app process (the Android equivalent of `sessionStorage`). `allowBackup` is off so the token never goes to a cloud backup.
  - The JWT `exp` claim is decoded (not verified). An expired token is never sent: `ApiClient` ends the session first. `SessionActivity` also logs out on a timer at `exp` while a screen is visible.
  - A 401 on any call clears the session and opens the login screen with "Tu sesión ha caducado. Vuelve a iniciar sesión." (`MultimediaApp.endSession`).
- `ApiClient` adds `Authorization: Bearer <jwt>` to every request, poster downloads included. The only exception is the video URL, which carries `?token=`.
- After a user changes their own username, the app forces a new login (the old token stops working).
