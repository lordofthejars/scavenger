package me.escoffier.keynote.messages;


import io.vertx.core.json.JsonObject;

public class ScoreFrame extends Frame {

    private final JsonObject json;
    private final int score;

    private ScoreFrame(JsonObject json) {
        super(TYPE.SCORE);
        int score = json.getInteger("score");
        this.json = json;
        this.score = score;
    }

    public int score() {
        return score;
    }
    
    @Override
    public JsonObject toJson() {
        return json;
    }

    public static ScoreFrame fromJson(JsonObject json) {
        return new ScoreFrame(json);
    }
}
