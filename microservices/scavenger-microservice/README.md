# scavenger-hunt-microservice
Implementation of the Game Server using Eclipse Vert.x.


## Dev mode:

```bash
mvn compile vertx:run
```

Server started on port 8080.

## Mobile client

### Initial build

```bash
cd src/main/resources/webroot
npm install && bower install
```

## Push to openshift

```bash
./update.sh
```


## IMPORTANT

1. update admin token
2. configure the data center as indicated in the config file - because you probably won't get a private cloud, private uses
 a Google Cloud instance