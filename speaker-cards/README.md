# jChampions speaker cards

A Java application that generates speaker cards, talk banners, and social images for the jChampions Conference. It automates the repetitive work of updating names, photos, session details, and schedules across conference graphics.

## What it does

- Imports speakers and talks from a Sessionize Excel schedule export and downloads speaker photos.
- Stores speaker and session data in PostgreSQL.
- Renders HTML templates as PNG images, individually or in a batch.
- Provides a local speaker directory with links to the generated cards.

Built with Quarkus, Renarde, Qute templates, Hibernate ORM with Panache, and Apache POI.

## Background

The motivation and implementation are described in [Automating Conference Assets with Java: The Quarkus System Behind jChampions 2026](https://www.the-main-thread.com/p/quarkus-jchampions-speaker-card-generator-tutorial) on The Main Thread.

## Run locally

Use IBM Semeru Java 27. Select the project's SDKMAN Java version:

```sh
sdk env
```

Place the 2027 Sessionize XLSX export in the project root. Check it and generate the cards (replace `Accepted2027.xlsx` with the export's filename):

```sh
./cards validate Accepted2027.xlsx
./cards generate Accepted2027.xlsx --output speaker-banners-2027
```

`./cards` builds and runs a Quarkus Picocli application. It uses Apache POI for both validation and import, and a temporary in-memory H2 database while generating cards. It starts its PNG renderer for the command and exits when done. No separate server or PostgreSQL container is needed. Output goes into `speaker/`, `talks/`, and `social/` beneath the chosen directory.

`validate` reports row, speaker, talk, and card counts. `generate` exits with an error if the schedule year differs from the branding year or the render count does not match. The supplied `AcceptedV5.xlsx` has 2026 dates; use `--allow-year-mismatch` only for a test run with it.

To save one card for review after importing, use a speaker UUID or talk ID from the export:

```sh
./cards preview Accepted2027.xlsx talk 1070177 --output preview.png
./cards preview Accepted2027.xlsx speaker SPEAKER_UUID --output preview.png
./cards preview Accepted2027.xlsx social SPEAKER_UUID --output preview.png
```

For the web directory, start `./mvnw quarkus:dev` with Docker or Podman running for its PostgreSQL database, then browse [localhost:8080](http://localhost:8080/). The web configuration recreates its database schema on startup. The importer keeps existing talk details on repeated imports, so restart the web app before importing a changed schedule.

Run `./mvnw verify` to build the application and test Excel import, PostgreSQL persistence, and PNG rendering. Docker or Podman must be running for the test database.

## 2027 artwork

The 2027 cards follow the new community supporter artwork: Poppins, teal and white backgrounds, orange/red segmented rings, and the waving Duke. Speaker and talk cards are 1280 × 720; social cards are 1080 × 1080.

![2027 speaker, talk, and social card previews](docs/images/2027/preview.png)

These previews use existing speaker photography with an illustrative talk and date. Actual session dates and times come from the imported schedule. Set the edition label with `conference.year` in `src/main/resources/application.properties`.

Card templates live in `src/main/resources/templates/Banner/`, including shared styles and a shared schedule footer. The original supporter SVGs are in `docs/artwork/2027/`. Derived SVG/PNG assets are in `src/main/resources/META-INF/resources/static/images/2027/`; bundled Poppins fonts and their license are in `static/fonts/` alongside them.

`python3 scripts/prepare-2027-artwork.py` regenerates the derived SVGs from the originals. After changing the artwork, export each derived SVG to its adjacent PNG at its declared dimensions; the PNG renderer uses those raster exports.

### Art direction

![jChampions Conference Community Supporter badge](docs/images/jchampions-supporter-dark.png)
