package me.escoffier.keynote.endpoints;

import io.vertx.core.json.JsonObject;
import me.escoffier.keynote.TestBase;
import org.junit.Test;

import static me.escoffier.keynote.Restafari.delete;
import static me.escoffier.keynote.Restafari.get;
import static me.escoffier.keynote.Restafari.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test the REST API to manage tasks.
 */
public class TaskEndpointTest extends TestBase {

    @Test
    public void testGettingTasks() {
        JsonObject json = new JsonObject(get("/admin/tasks").asString());
        assertThat(json.fieldNames()).isNotEmpty();
        json.fieldNames().forEach(name -> {
            JsonObject object = json.getJsonObject(name);
            assertThat(object).isNotNull();
            assertThat(object.getString("description")).isNotBlank();
            assertThat(object.getString("object")).isNotBlank();
            assertThat(object.getString("type")).isNotBlank();
            assertThat(object.getInteger("point", -1)).isGreaterThan(0);
        });
    }

    @Test
    public void testAddingATask() {
        JsonObject newTask = new JsonObject()
            .put("description", "phone")
            .put("type", "map")
            .put("stage", 1)
            .put("object", "phone")
            .put("point", 5);
        
        JsonObject json = new JsonObject(given().body(newTask.encode()).post("/admin/tasks").asString());
        assertThat(json.getString("description")).isEqualTo("phone");
        assertThat(json.getString("type")).isEqualTo("map");
        assertThat(json.getString("object")).isEqualTo("phone");
        assertThat(json.getInteger("point")).isEqualTo(5);
        assertThat(json.getInteger("stage")).isEqualTo(1);
        String taskId = json.getString("taskId");
        assertThat(taskId).isNotBlank();

        // Check that the task have been added
        JsonObject all = new JsonObject(get("/admin/tasks").asString());
        assertThat(all.fieldNames()).isNotEmpty();
        JsonObject added = all.getJsonObject(taskId);
        assertThat(added).isNotNull();
        assertThat(added.getString("description")).isEqualTo("phone");
        assertThat(added.getString("type")).isEqualTo("map");
        assertThat(added.getInteger("stage")).isEqualTo(1);
        assertThat(added.getString("object")).isEqualTo("phone");
        assertThat(added.getInteger("point")).isEqualTo(5);
    }

    @Test
    public void testDeletingATask() {
        String taskId = "d1eea44d-5b59-44a8-b77c-77532da51e8b";

        delete("/admin/tasks/" + taskId).then().statusCode(204);

        // Check that the task have been deleted
        JsonObject all = new JsonObject(get("/admin/tasks").asString());
        assertThat(all.fieldNames()).isNotEmpty();
        JsonObject deleted = all.getJsonObject(taskId);
        assertThat(deleted).isNull();
    }

    @Test
    public void testUpdatingATask() {
        String taskId = "d1eea44d-5b59-44a8-b77c-77532da51e8b";
        JsonObject json = new JsonObject()
            .put("description", "phone-2")
            .put("type", "map")
            .put("object", "phone")
            .put("point", 5)
            .put("stage", 2);

        JsonObject task = new JsonObject(given().body(json.encode()).put("/admin/tasks/" + taskId).asString());
        assertThat(task.getString("description")).isEqualTo("phone-2");
        assertThat(task.getString("object")).isEqualTo("phone");
        assertThat(task.getString("type")).isEqualTo("map");
        assertThat(task.getInteger("stage")).isEqualTo(2);
        assertThat(task.getInteger("point")).isEqualTo(5);

        JsonObject all = new JsonObject(get("/admin/tasks").asString());
        assertThat(all.fieldNames()).isNotEmpty();
        JsonObject edited = all.getJsonObject(taskId);
        assertThat(edited.getString("description")).isEqualTo("phone-2");
        assertThat(task.getString("object")).isEqualTo("phone");
        assertThat(edited.getString("type")).isEqualTo("map");
        assertThat(edited.getInteger("stage")).isEqualTo(2);
        assertThat(task.getInteger("point")).isEqualTo(5);
    }

    //TODO Test listen.

}
