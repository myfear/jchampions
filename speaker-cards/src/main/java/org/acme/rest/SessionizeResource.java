package org.acme.rest;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

import org.acme.startup.SessionizeCache;
import org.acme.startup.SessionizeImporter;
import org.acme.startup.SessionizeSchedule;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/sessionize")
@Produces(MediaType.APPLICATION_JSON)
public class SessionizeResource {
    @Inject
    SessionizeCache cache;
    @Inject
    SessionizeImporter importer;

    public record RefreshResult(String path, Instant timestamp, SessionizeSchedule.Summary summary) {}

    @POST
    @Path("/refresh")
    public RefreshResult refresh() throws IOException {
        var snapshot = cache.refresh();
        return new RefreshResult(snapshot.path(), snapshot.timestamp(), snapshot.schedule().summary());
    }

    @POST
    @Path("/import")
    public SessionizeSchedule.Summary importSchedule() throws IOException {
        var schedule = cache.load().schedule();
        importer.importSchedule(schedule);
        return schedule.summary();
    }

    @ServerExceptionMapper
    Response upstreamFailure(SessionizeCache.UpstreamException e) {
        return Response.status(502).entity(Map.of("error", e.getMessage())).build();
    }

    @ServerExceptionMapper
    Response invalidData(IllegalArgumentException e) {
        return Response.status(422).entity(Map.of("error", e.getMessage())).build();
    }
}
