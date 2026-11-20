# jChampions speaker cards

A Java application that generates speaker cards, talk banners, and social images for the jChampions Conference. It automates the repetitive work of updating names, photos, session details, and schedules across conference graphics.

## What it does

- Imports scheduled and accepted talks and their speakers from the Sessionize API and downloads missing speaker photos.
- Stores speaker and session data in PostgreSQL.
- Renders HTML templates as PNG images, individually or in a batch.
- Provides a local speaker directory with links to the generated cards.

Built with Quarkus, Renarde, Qute templates, Hibernate ORM with Panache, and Jackson.

## Background

The motivation and implementation are described in [Automating Conference Assets with Java: The Quarkus System Behind jChampions 2026](https://www.the-main-thread.com/p/quarkus-jchampions-speaker-card-generator-tutorial) on The Main Thread.

## Run locally

Run these commands from the `speaker-cards` directory using IBM Semeru Java 27.

```sh
sdk env install
sdk env
./cards validate
./cards generate --output speaker-banners-2027
```

The first command that needs schedule data makes one GET to the [Sessionize All endpoint](https://sessionize.com/api/v2/tnkvbkcy/view/All) and saves the validated response in `.cache/sessionize-all.json`. Subsequent commands can use that file offline. The web app also reuses the parsed schedule in memory. Override `sessionize.url` and `sessionize.cache-file` with Quarkus configuration or the environment variables `SESSIONIZE_URL` and `SESSIONIZE_CACHE_FILE`.

Run `./cards refresh` to fetch a new snapshot. Refresh validates the response before atomically replacing the file. If Sessionize is unavailable or the feed is empty, invalid, or has no eligible talks, the command exits with an error and preserves the last successful snapshot.

`validate` reports included sessions, skipped sessions, unique speakers, expected cards, and any published schedule years. Each speaker gets two cards and each talk gets one. `generate` checks the year against the card branding and verifies the number of distinct files written. Use `--allow-year-mismatch` on `generate` or `preview` for a test run with another year's schedule.

`./cards` builds and runs the Quarkus CLI. Generation and preview use an in-memory H2 database and start a local PNG renderer. Batch output goes into `speaker/`, `talks/`, and `social/` beneath the selected directory. Speaker filenames use `LastName_FirstName.png`. Talk filenames start with the speakers’ surnames in alphabetical order, joined by underscores, followed by up to six words of the title (48 UTF-8 bytes). For example, `Grzesik_Pałka_fast-pain-free-java-and-native.png`. When two filenames would match, each gets its Sessionize ID as a suffix.

To render a single card, use a talk ID or speaker UUID from the cached JSON.

```sh
./cards preview talk 1070177 --output preview.png
./cards preview speaker SPEAKER_UUID --output preview.png
./cards preview social SPEAKER_UUID --output preview.png
```

## Web directory

Start `./mvnw quarkus:dev` with Docker or Podman running for PostgreSQL. The app recreates its database schema on startup and waits for an explicit import. The POST endpoints use Renarde's CSRF protection. Read the token from the [local speaker directory](http://localhost:8080/) and send it with the request. Import uses the current cache, fetching once if the file is missing.

```sh
token=$(curl -fsS -o /dev/null -c - http://localhost:8080/ | awk '$6 == "csrf-token" {print $7}')
sessionize_post() {
    curl --fail-with-body -X POST -b "csrf-token=$token" -H "X-CSRF-TOKEN: $token" \
        "http://localhost:8080/api/sessionize/$1"
}
sessionize_post import
```

To apply a schedule change, refresh the cache and then import it.

```sh
sessionize_post refresh
sessionize_post import
```

Refresh returns the cache path, timestamp, and summary. Import returns the summary after updating PostgreSQL. Both endpoints return HTTP 502 for an upstream request failure and 422 for invalid or empty schedule data.

## Sessionize field mapping

Accepted talks with speakers can generate cards before the timetable is published. Their cards omit the date and time. Once Sessionize publishes the times, run `./cards refresh` and `./cards generate` to include them. Service sessions, talks without speakers, and unscheduled talks without accepted status are skipped. Import joins each included session's speaker UUIDs to the feed's speaker records. It updates names, taglines, optional biographies, titles, descriptions, live links, and talk membership on repeated imports. Talks and speakers absent from the latest schedule stay in the database. Missing photos use the existing Duke artwork until a photo is available.

Session times with an explicit offset use that offset. Times without one use `America/New_York`. The card shows the local times in New York and Paris and takes its date from Paris. The duration in minutes comes from `endsAt - startsAt`, and `liveUrl` supplies the talk's live link.

Run `./mvnw verify` with Docker or Podman running to test caching, PostgreSQL import, and PNG rendering. The tests use local Sessionize fixtures.

## 2027 artwork

The 2027 cards follow the new community supporter artwork: Poppins, teal and white backgrounds, orange/red segmented rings, and the waving Duke. Speaker and talk cards are 1280 × 720; social cards are 1080 × 1080.

![2027 speaker, talk, and social card previews](docs/images/2027/preview.png)

These previews use existing speaker photography with an illustrative talk and date. Actual session dates and times come from the imported schedule. Set the edition label with `conference.year` in `src/main/resources/application.properties`.

Card templates live in `src/main/resources/templates/Banner/`, including shared styles and a shared schedule footer. The original supporter SVGs are in `docs/artwork/2027/`. Derived SVG/PNG assets are in `src/main/resources/META-INF/resources/static/images/2027/`; bundled Poppins fonts and their license are in `static/fonts/` alongside them.

`python3 scripts/prepare-2027-artwork.py` regenerates the derived SVGs from the originals. After changing the artwork, export each derived SVG to its adjacent PNG at its declared dimensions; the PNG renderer uses those raster exports.

### Art direction

![jChampions Conference Community Supporter badge](docs/images/jchampions-supporter-dark.png)
