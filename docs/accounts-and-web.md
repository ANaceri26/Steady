# Steady accounts and web preview

## Current status

The React/Vite web app and Android preview use Supabase email/password authentication, a private Storage bucket, and one revisioned workspace record per account. The apps remain usable locally. Web automatically attempts synchronization after changes and on focus; Android has an explicit **Settings → Sync now** action. This is snapshot synchronization, not field-by-field merging. Conflicting device edits require a backup and an explicit choice before replacement.

The web app is currently a local preview, not a public deployment. Version 1.0.0 and its first GitHub release must wait for successful real-account, cross-device and deployment tests. No historical preview releases have been created.

## Local configuration

Web uses ignored `web/.env.local`:

```text
VITE_SUPABASE_URL=https://PROJECT.supabase.co
VITE_SUPABASE_PUBLISHABLE_KEY=PUBLIC_KEY
```

Android uses ignored `local.properties` alongside the SDK path:

```text
steady.supabase.url=https://PROJECT.supabase.co
steady.supabase.key=PUBLIC_KEY
steady.web.url=http://127.0.0.1:5174/
```

These public values are bundled into each app. Never include a service-role key, secret key or database password. Security depends on the database and Storage policies, not hiding the publishable key.

Run `npm ci`, `npm run dev -- --port 5174`, `npm test`, and `npm run build` from `web`. Rollup's WebAssembly distribution avoids blocked native modules on Windows. Build Android with the instructions in BUILDING.md. Run Android tests before web tests to generate `app/build/interop/android.zip`; the web tests generate the return fixture.

## Supabase setup

1. Create a free project with Data API enabled and automatic RLS enabled. Keep automatic table exposure disabled.
2. Apply `supabase/migrations/202611170001_accounts.sql` once. It explicitly grants authenticated read access to the user's own workspace and authenticated execution of the checked commit function. Anonymous reads/writes remain denied.
3. Enable email authentication and email confirmation. Require a strong password (the clients request 12+ characters for signup).
4. For local testing set Site URL and allowed Redirect URL to `http://127.0.0.1:5174/`. For public deployment replace Site URL with the final HTTPS URL and add that exact URL to the redirect allowlist.
5. Configure custom SMTP before public signup: Supabase's default sender is restricted and unsuitable for a public release. Configure rate limits and an appropriate abuse-protection flow before opening registration widely.
6. Create test accounts through the UI, never by sharing passwords with a coding assistant.

## Data and account boundaries

- Web local storage uses a separate IndexedDB entry for each user UUID and for the guest workspace. Android uses a separate SQLite database per UUID; the original `steady.db` remains the guest database.
- Signing in does not upload or merge guest data. Export a guest backup and import it while signed in if that transfer is wanted.
- Android encrypts persisted session tokens with an AES-GCM key in Android Keystore. Passwords are never saved. Web session persistence is managed by the Supabase client; don't insert untrusted HTML or third-party scripts into the app.
- Sign-out returns to the guest workspace. Existing account data remains on that device for later sign-in; clearing browser/app storage removes local copies.
- The server chooses account ownership from the verified session, never from a client-provided user ID in a write request.
- Each upload has a unique immutable object name. The commit function checks the expected revision atomically. A conflict cannot silently replace newer data.
- Local backup limits remain 256 MiB expanded / 16 MiB manifest. Cloud snapshots are limited to 20 MiB compressed. Settings travel in the workspace record; offline backup v1 carries content, not preferences.
- Cloud files contain private data and are not end-to-end encrypted. Storage access is limited to the owner account. Offline ZIPs are not encrypted.
- Native reminders and permissions stay on Android. The browser does not ring background alarms.

## Release gate for v1.0.0

Before tagging or publishing:

- Verify email signup, confirmation, sign-in, password reset, expiry/refresh, and sign-out on real devices.
- Use two test accounts to verify one cannot read or replace the other's data or files.
- Test Android → web and web → Android including photo attachments, groups, project docs, task times, goals, completion history, preferences and workouts.
- Edit both devices offline and verify conflict handling keeps both copies recoverable.
- Verify guest/account switching and browser refresh retain the right workspace.
- Test the deployed HTTPS URL, auth redirects, phone layout and journal exports.
- Configure production email delivery and keep release signing keys outside Git.
- After user testing, commit and push, create annotated tag `v1.0.0`, and publish the first GitHub release with checked artifacts and release notes. Do not present a debug preview APK as a Play Store production release.
