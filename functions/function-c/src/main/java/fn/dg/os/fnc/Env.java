package fn.dg.os.fnc;

import com.google.gson.JsonObject;

/**
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class Env {

    private static JsonObject env;

    public static String getOrDefault(String key, String def) {
        if (env.has(key)) {
            String value = env.get(key).getAsString();
            if (!value.isEmpty()) {
                return value;
            }
        }
        return def;
    }

    public static int getOrDefault(String key, int def) {
        if (env.has(key)) {
            return env.get(key).getAsInt();
        }
        return def;
    }

    public static void init(JsonObject request) {
        env = request;
    }
}
