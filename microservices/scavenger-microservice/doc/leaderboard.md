# Leader board

The leader board is retrieved from the microservice B. The url is configured in the config map using the `leader-board-url`
 entry in the corresponding location. If not set a fake leader board is generated.
 
 This microservice delegates to the microservice B to retrieve the leader board. It augments the responses with a few 
 additional data and change the JSON format.
 
To retrieve the leader board, use `GET /leaderboard`. It returns an object as:

```json
{
    "scores": [{
        "name": "Pitch Fancier",
        "score": 100,
        "playerId": "cedef59e-7c6c-4c41-86cd-8f650aa81820"
    }, {
        "name": "Maze Hiss",
        "score": 95,
        "playerId": "18bf7396-327f-40dc-a5db-4455905bdde0"
    }, {
        "name": "Quartz Condor",
        "score": 90,
        "playerId": "443c5717-9699-45f7-8b01-fc602b57142c"
    }, {
        "name": "Ember Dive",
        "score": 85,
        "playerId": "415dea6c-4acb-40c5-9e5e-b6c152616cb2"
    }, {
        "name": "Ballistic Fright",
        "score": 80,
        "playerId": "9e2a9320-3b16-48c0-9698-840e2e3caed1"
    }, {
        "name": "Weak Crow",
        "score": 75,
        "playerId": "54595f2f-314d-44a6-a490-38d58395d3ef"
    }, {
        "name": "Slime Unicorn",
        "score": 70,
        "playerId": "cf061228-4410-45e8-a0de-fb40bb526185"
    }, {
        "name": "Slash Cat",
        "score": 65,
        "playerId": "ee867d11-5ec1-48cc-8d02-23b95ee9795c"
    }, {
        "name": "Fir Witch",
        "score": 60,
        "playerId": "8eb69e5e-a901-45ec-9135-0503e63cb948"
    }, {
        "name": "Grove Stalker",
        "score": 55,
        "playerId": "38ae4e1d-686c-45cd-a362-733b4345b9b8"
    }],
    "active-players": 58
}
```

If called with the `playerId` query parameter as: `GET /leaderboard?playerId=....`, the response also contains the:

* rank - current player rank
* player id (`playerId`), player name (`playerName`), current score (`score`) 

