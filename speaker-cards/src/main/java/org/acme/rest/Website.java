package org.acme.rest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.acme.model.Speaker;
import org.acme.model.Talk;
import org.acme.service.CardFilenames;

import io.quarkiverse.renarde.Controller;
import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.Path;

public class Website extends Controller {

    @CheckedTemplate
    static class Templates {
        public static native TemplateInstance index(List<Speaker> speakers, Map<UUID, String> speakerNames, Map<Long, String> talkNames);
    }

    @Path("/")
    public TemplateInstance index() {
        List<Speaker> speakers = Speaker.listAll();
        List<Talk> talks = Talk.list("select distinct t from Talk t left join fetch t.speakers");
        return Templates.index(speakers, CardFilenames.speakers(speakers), CardFilenames.talks(talks));
    }

}
