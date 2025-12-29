# speaker-cards

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.



Import speakers from sessionize export:
curl "http://localhost:8080/api/import/csv/SelectedWithSchedule.xlsx"

Generate Speaker Banners:
curl "http://localhost:8080/api/banners/generate-all?outputDir=./speaker-banners"


## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

> **_NOTE:_**  Quarkus now ships with a Dev UI, which is available in dev mode only at <http://localhost:8080/q/dev/>.

## Development Mode Limitations

**Important**: The current implementation only works in development mode due to the following limitations:
  - Speaker photos are downloaded to `src/main/resources/META-INF/speaker/` which is not writable in production JAR files.
  - Database configuration uses Quarkus Dev Services (requires Docker).
  - File paths are hardcoded for development environment.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar` but, of course, it will require a 
PostgreSQL server to run locally, otherwise it will fail.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

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

This is a small Renarde webapp. Once the quarkus app is started visit http://localhost:8080/renarde

[Related guide section...](https://quarkiverse.github.io/quarkiverse-docs/quarkus-renarde/dev/index.html)

# TODO - Production Support

The following items need to be implemented for production deployment.

## Database Configuration

  - Add production PostgreSQL configuration with environment variables.
  - Create Docker Compose setup with PostgreSQL container.
  - Add database migration scripts.

## File Storage

  - Move speaker photo storage from resources to external directory (e.g., `/app/speaker-photos/`)
  - Add volume mapping for persistent photo storage in Docker.
  - Update Banner controller to serve photos from filesystem instead of resources.

## Docker Support

  - Add docker-compose.yml with PostgreSQL and application services
  - Configure production datasource for containerized PostgreSQL

## Example of Production Setup

    # docker-compose.yml (TODO)
    version: '3.8'
    services:
      postgres:
        image: postgres:15
        environment:
          POSTGRES_DB: speakercards
          POSTGRES_USER: postgres
          POSTGRES_PASSWORD: postgres
      volumes:
        - postgres_data:/var/lib/postgresql/data

      app:
        build: .
        ports:
          - "8080:8080"
        environment:
          DATABASE_URL: jdbc:postgresql://postgres:5432/speakercards
          DATABASE_USER: postgres
          DATABASE_PASSWORD: postgres
        volumes:
          - speaker_photos:/app/speaker-photos
        depends_on:
          - postgres

    volumes:
      postgres_data:
      speaker_photos:
