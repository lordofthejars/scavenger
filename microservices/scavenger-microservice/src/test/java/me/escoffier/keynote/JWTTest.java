package me.escoffier.keynote;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jwt.JWK;
import io.vertx.ext.jwt.JWT;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class JWTTest {

    @Test
    public void test() {
        Vertx vertx = Vertx.vertx();
        Buffer buffer = vertx.fileSystem().readFileBlocking("certs.json");
        JWT jwt = new JWT();
        jwt.addJWK(new JWK(buffer.toJsonObject().getJsonArray("keys").getJsonObject(0)));

        JsonObject result = jwt.decode("eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICI0V0xtZy1EbXQ5X2c4RzNYbVJCNnBIQWJTR1hsdXZDUGdLMDBYa2dtRkVrIn0.eyJqdGkiOiJkNjc5NDFlOC03MDZkLTQwYjAtOTBiNi05MTk4NDFiNzBlN2IiLCJleHAiOjE1MjIzMjgxNDQsIm5iZiI6MCwiaWF0IjoxNTIyMzI3MjQ0LCJpc3MiOiJodHRwczovL3NlY3VyZS1zc28tc3NvLmFwcHMuc3VtbWl0LWF3cy5zeXNkZXNlbmcuY29tL2F1dGgvcmVhbG1zL3N1bW1pdCIsImF1ZCI6ImdhbWUiLCJzdWIiOiJiN2RiYTRhOS02MDg1LTQ0MWItYjVhYi1hNzc4M2VlMTI1MzEiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJnYW1lIiwibm9uY2UiOiJiYjRlMzgxOC0zMjVlLTQ0ODctODU4Zi0xODA0NjgzMWM4NWUiLCJhdXRoX3RpbWUiOjE1MjIzMjYzOTQsInNlc3Npb25fc3RhdGUiOiI5NzVlMjA4Ny1iYmQ3LTRlOWQtODhiYy04MDZlNmY2ODk1NWUiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbIioiXSwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbInVtYV9hdXRob3JpemF0aW9uIl19LCJyZXNvdXJjZV9hY2Nlc3MiOnsiYWNjb3VudCI6eyJyb2xlcyI6WyJtYW5hZ2UtYWNjb3VudCIsIm1hbmFnZS1hY2NvdW50LWxpbmtzIiwidmlldy1wcm9maWxlIl19fSwibmFtZSI6ImNsZW1lbnQgZXNjb2ZmaWVyIiwicHJlZmVycmVkX3VzZXJuYW1lIjoiY2xlbWVudC5lc2NvZmZpZXJAZ21haWwuY29tIiwiZ2l2ZW5fbmFtZSI6ImNsZW1lbnQiLCJmYW1pbHlfbmFtZSI6ImVzY29mZmllciIsImVtYWlsIjoiY2xlbWVudC5lc2NvZmZpZXJAZ21haWwuY29tIn0.QsUUbUdPUryk4ttCQoQcA2ibKKvGpgaCZ4ryYrIrw9zD-N5xn6sPPNK_WB3bkUaNB-RkQoFbBCcvO0ExNDPTSGnv7AHOISTJ_2oRx2vVeXnkhl4ynXshHeSYYw7R3qeAai_vlIVdA8FfFZ54ar6Evzj0dL4u_6wABG4coC6SHLkvR7BzAPA1sMqUS0M_4WM7oTz7ymfdbw6RFw5c-AJ6IznBsj5H13dH8d74w-RQhQAaY2v2TZ74upv7WRdRHivMJfJNlb_MII3-RoAjBMG2q8WeOKITM_CpuLFatcTW6uZ_8EHon8Zm1_QKgb12CP85zDKchU4lqEs3eq4Tma3N2Q");
        
        assertThat(result.getString("name")).isEqualTo("clement escoffier");
        vertx.close();
    }
}
