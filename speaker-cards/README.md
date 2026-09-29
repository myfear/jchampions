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

Use IBM Semeru Java 27 and a running Docker or Podman environment for the development PostgreSQL database. Select the project's SDKMAN Java version and start the application:

```sh
sdk env
./mvnw quarkus:dev
```

Place `SelectedWithSchedule.xlsx` in the project root, then import it and generate all cards:

```sh
curl "http://localhost:8080/api/import/csv/SelectedWithSchedule.xlsx"
curl "http://localhost:8080/api/banners/generate-all?outputDir=./speaker-banners"
```

Browse the [local speaker directory](http://localhost:8080/). Generated PNGs are saved under `speaker-banners/` in the `speaker/`, `talks/`, and `social/` folders. The current configuration recreates the database schema on startup.

Run `./mvnw verify` to build the application and test Excel import, PostgreSQL persistence, and PNG rendering. Docker or Podman must be running for the test database.

## Artwork

Card templates live in `src/main/resources/templates/Banner/`; images and fonts live in `src/main/resources/META-INF/resources/static/`.

The community sponsorship artwork uses a yearless **Community Supporter** design.

![jChampions Conference Community Supporter badge](docs/images/jchampions-supporter-dark.png)
