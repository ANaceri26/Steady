# Steady portable backup v1

Status: implemented on Android. The web app and future iOS app must implement this contract before cross-platform import is available. This is manual snapshot transfer, not live synchronization or merging.

## User workflow

1. In Settings, choose **Save full backup** and save to local storage.
2. Copy the ZIP to the other device using USB or local file sharing. No Steady account or internet connection is needed. Cloud locations offered by the system file picker may require internet.
3. In the receiving app, choose **Import backup**. Confirm replacement of that app's existing data. Save its own backup first if needed.
4. Check alarm permissions on the receiving device. Reminder times use its local time zone.

Journal **Export all** and the Settings journal export buttons produce readable documentation, not restorable backups. The photo ZIP contains `journal.html` and referenced images, including project names, groups, progress, lessons, and next steps. Extract the ZIP and open the HTML to read it offline.

## Container and compatibility

A ZIP contains UTF-8 `steady.json` and optional `media/1.jpg`, `media/2.jpg`, etc. All names are relative and unique; directory entries, traversal, and unexpected names are rejected. Photos retain their encoded bytes. Current Android photo ingestion normalizes images to JPEG.

The manifest is a JSON object:

```json
{"format":"steady-backup","version":1,"data":{"projects":[],"items":[],"entries":[],"journal_groups":[],"journal":[],"photos":[],"journal_photos":[],"gym_routines":[],"gym_exercises":[],"gym_sessions":[],"gym_sets":[],"snoozes":[]}}
```

Every collection must be present, even when empty. Every record must contain all fields listed below. Unknown versions or fields are rejected rather than silently losing data. Future schema changes require a new version and explicit migration support. This is JSON data, not a SQLite file; importers must never execute SQL from a backup.

IDs and references are integers; nullable references use JSON `null`. Preserve IDs within the snapshot. Flags use integer `0`/`1`, not JSON booleans. `signal_override: null` inherits the project's signal flag (or Noise without a project). Strings, including user notes, must be treated as untrusted text and escaped in HTML.

| Collection | Fields |
| --- | --- |
| projects | id, title, notes, signal |
| items | id, title, notes, section, project (nullable), goal, scheduled, enabled, repeat, minute, start, signal_override (nullable) |
| entries | item, period, status, updated |
| journal_groups | id, name |
| journal | id, item (nullable), title, period, progress, obstacle, next, created, group_id (nullable), kind, project_title |
| photos | id, item, path |
| journal_photos | id, journal, path |
| gym_routines | id, name |
| gym_exercises | id, routine, name, unit |
| gym_sessions | id, routine (nullable), name, date, notes |
| gym_sets | id, session, exercise (nullable), name, position, reps, weight, unit |
| snoozes | item, period, at |

`items` represents both tasks and goals (`goal=1`). `start` and gym `date` are ISO local dates. `minute` is minutes since midnight (0–1439). `repeat` is 0=once, 1=daily, 2=weekly, 3=monthly. `updated`, `created` and `at` are Unix epoch milliseconds. Period keys and completion statuses must be preserved exactly. Journal kinds are `Journal`, `Advice`, or `Project log`; group names are unique ignoring case and contain 1–40 trimmed characters. `weight` may be fractional; other numeric fields are integers. Photo paths reference archive media names, never an originating device path.

## Import guarantees and limits

Android validates the complete archive, required fields, primitive types, schedules, constraints, photo references and foreign-key relationships before replacement. All records are replaced in one transaction. Failed validation leaves existing records untouched and removes staged files. Media is staged under a fresh app-private directory so failed imports cannot overwrite existing photos. Existing unreferenced private photo files are retained to avoid races with other readers; they are not exported.

Limits: 256 MiB total uncompressed data, 16 MiB manifest, 10,000 archive entries, 100,000 records per collection. Imports reject unsupported or incomplete snapshots. Backups are not encrypted: anyone with the file can read its contents. Keep it somewhere private.

Backups include tasks, goals, projects, completion history, journals and groups, referenced photos, workout data and snooze records. Device permissions, custom alarm sounds, Android settings and app installation are excluded. Alarms are rescheduled after import; expired snoozes are cleared. Offline web and iOS reminder behavior must be implemented for those platforms separately.
