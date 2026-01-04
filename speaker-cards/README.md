# speaker-cards

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Automated CSV Import and Banner Generation

The application now features a fully automated pipeline that handles CSV import, speaker photo downloads, and banner
generation in a single operation:

    curl http://localhost:8080/api/import/csv

This single command will:

  - Import speakers and talks from CSV data.
  - Download speaker profile photos.
  - Generate all banner types (speaker, talk, and social banners).
  - Save banners to `./speaker-banners/` directory.

The pipeline is built using Apache Camel routes and processes data through the following stages:

  - CSV Processing: parses speaker and talk data using Camel Bindy.
  - Database Import: persists entities using Hibernate ORM Panache.
  - Photo Download: downloads speaker photos via HTTP.
  - Banner Generation: creates PNG banners using Quarkus PDF/image generation

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

    $ mvn clean quarkus:dev
    $ curl http://localhost:8080/api/import/csv
    $ open http://localhost:8080

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Development Mode Limitations

**Important**: The current implementation only works in development mode due to the following limitations:
  - Speaker photos are downloaded to `src/main/resources/META-INF/speaker/` which is not writable in production JAR files.
  - Database configuration uses Quarkus Dev Services (requires Docker).
  - File paths are hardcoded for development environment.

## Production Deployment
The application is production-ready with Docker Compose support:

    # Build the application and start all services (PostgreSQL + Application)
    $ mvn clean install
    $ curl http://localhost:8080/api/import/csv
    $ open http://localhost:8080

### Production Features

  - PostgreSQL Database: containerized database with persistent storage.
  - File Storage: external volume mounts for photos and banners.
  - Automated Pipeline: same CSV import endpoint works in production.
  - Health Checks: database health monitoring and service dependencies.

### Docker Services

  - Application: http://localhost:8080
  - Database Admin: http://localhost:8085 (Adminer)
  - PostgreSQL: localhost:5432

### Infrastructure

The software infrastructure is as shown below.

#### Technology Stack

  - Quarkus: Supersonic Subatomic Java Framework.
  - Apache Camel: Integration framework for CSV processing pipeline.
  - Hibernate ORM Panache: Database persistence layer.
  - PostgreSQL: Production database
  - Renarde: Server-side web framework with PDF/image generation
  - Docker: Containerization and orchestration

#### Key Components

  - `CsvImportRouter`: Camel route orchestrating the import pipeline.
  - Processors: Transactional processors for speakers, talks, photos, and banners.
  - Entities: Speaker and Talk JPA entities with Panache.
  - Banner Generation: PDF-to-PNG conversion for various banner types.

#### API Endpoints


The following are the API endpoints:

  - GET /api/import/csv - triggers automated import pipeline.
  - GET /speaker-banner/{id}.png - generates a speaker banner.
  - GET /talk-banner/{id}.png - generate a talk banner.
  - GET /speaker-social/{id}.png - generate social media banner.
  - GET /speaker-photo/{id} - serves a speaker photos

#### File Structure

    ./csv-input/          # CSV processing directory
    ./speaker-photos/     # Downloaded speaker photos
    ./speaker-banners/    # Generated banner files
      ├── speaker/        # Speaker banners
      ├── talks/          # Talk banners
      └── social/         # Social media banners

## Development vs Production

The application automatically adapts file paths based on the environment:

  - Development: uses relative paths in project directory.
  - Production: uses absolute paths with Docker volume mounts.

## Packaging and running the application

The application can be run in Quarkeus dev mode using:

    mvnw quarkus:dev

or in production-ready mode using:

    mvn clean install

In the later case, all the required Docker images will be started, as shown below:

    $ docker ps
    CONTAINER ID   IMAGE                                 COMMAND                  CREATED          STATUS                    PORTS                                                                                                NAMES
    794da1574773   markuseisele/jchamps:1.0.0-SNAPSHOT   "java -agentlib:jdwp…"   15 seconds ago   Up 4 seconds              0.0.0.0:5005->5005/tcp, [::]:5005->5005/tcp, 0.0.0.0:8080->8080/tcp, [::]:8080->8080/tcp, 8443/tcp   jchamps
    a594f67f20af   adminer                               "entrypoint.sh docke…"   15 seconds ago   Up 4 seconds              0.0.0.0:8085->8080/tcp, [::]:8085->8080/tcp                                                          adminer
    cafcd440ba5c   postgres:latest                       "docker-entrypoint.s…"   15 seconds ago   Up 14 seconds (healthy)   0.0.0.0:5432->5432/tcp, [::]:5432->5432/tcp                                                          postgresql

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/speaker-cards-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- Renarde ([guide](https://quarkiverse.github.io/quarkiverse-docs/quarkus-renarde/dev/index.html)): Renarde is a server-side Web Framework based on Quarkus, Qute, Hibernate and RESTEasy Reactive.

## Provided Code

### Renarde

This is a small Renarde webapp. Once the quarkus app is started visit http://localhost:8080

[Related guide section...](https://quarkiverse.github.io/quarkiverse-docs/quarkus-renarde/dev/index.html)
