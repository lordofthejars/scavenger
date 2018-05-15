# Production

Player ranking and top 10:

```bash
curl  http://microservice-b-microservice-b.apps.summit-aws.sysdeseng.com/rank/:playerId
```

Leaderboard for dashboard:

```bash
curl  http://microservice-b-microservice-b.apps.summit-aws.sysdeseng.com/leaderboard
```

Score websocket endpoint:

    ws://microservice-b-microservice-b.apps.summit-aws.sysdeseng.com/dashboard

Sockjs score endpoint:

    ws://microservice-b-microservice-b.apps.summit-aws.sysdeseng.com/scores/websocket


# Test

Test players cache: `indexed4`
Test scores cache: `default4`

Starts injection of data in a separate cache from `players` production one:

```bash
curl http://microservice-b-microservice-b.apps.summit-gce.sysdeseng.com/test/inject
```

Gets the test leaderboard:
```bash
curl http://microservice-b-microservice-b.apps.summit-gce.sysdeseng.com/test/leaderboard
```

Stops the test injector and clears the test caches.
If you call the leaderboard after this, it'd be empty of course.
```bash
curl http://microservice-b-microservice-b.apps.summit-gce.sysdeseng.com/test/inject/stop
```

Test score websocket endpoint:

    ws://microservice-b-microservice-b.apps.summit-gce.sysdeseng.com/test/dashboard
