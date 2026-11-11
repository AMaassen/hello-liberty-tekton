package demo;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** GET /api/hello */
@Path("/hello")
public class HelloResource {

    static final String MESSAGE = "Hallo vanuit Open Liberty op OpenShift, gebouwd door Tekton (gestart door git push)";

    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String hello() {
        String pod = System.getenv().getOrDefault("HOSTNAME", "lokaal");
        return MESSAGE + " (pod: " + pod + ")";
    }
}
