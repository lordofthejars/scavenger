# Build Ayers

```mvn package```

# Download an image call it img.jpg

# Start https://github.com/rhdemo/imagescorer


# Run Ayers from the command line with local file

```java -Dlog4j.configurationFile=$PWD/log4j2.properties -jar target/ayers-1.0-SNAPSHOT.jar imageFile=img.jpg```


# Run Ayers from the command line with file in S3

```
source environment
curl -i -X PUT http://$S3_AWS_HOST/v1/AUTH_gv0/ayers
curl -i -X PUT -T img.jpg http://$S3_AWS_HOST/v1/AUTH_gv0/ayers/img.jpg
java -Dlog4j.configurationFile=$PWD/log4j2.properties -jar target/ayers-1.0-SNAPSHOT.jar "$(envsubst < swiftObj.json)"

```


# Running in OpenWhisk

## Create the action

First we need to build the jar file and create an OpenWhisk action
from it.

```
source environment
mvn clean test package
wsk -i action update ayers target/ayers-1.0-SNAPSHOT.jar --main com.redhat.summit2018.FunctionB \
 --web=true \
 -p model-endpoint $MODEL_ENDPOINT \
 -p jdg-url $JDG_URL \
 -p microservice-endpoint $MICROSERVICE_ENDPOINT
```

## Invoke the action manually

Then we can test the action manually, passing a Red Hat Shadowman logo
image to the scoring engine which helpfully scores it as a person.

```
wsk -i action invoke ayers --blocking -p swiftObj '{"container": "img", "method": "PUT", "object": "logo.png", "token": "BOGUS", "url": "https://www.redhat.com/profiles/rh/themes/redhatdotcom/"}'
wsk -i activation logs <activation_id>
```

## Create and invoke a trigger and rule for the action

After firing the trigger, we list the activations. You may have to
issue that command a couple of times until you see the ayersTrigger
and ayers activations show up.

```
wsk -i trigger create ayers-trigger
wsk -i rule create ayers-rule ayers-trigger ayers
wsk -i trigger fire ayers-trigger -p swiftObj '{"container": "img", "method": "PUT", "object": "logo.png", "token": 
"BOGUS", "url": "https://www.redhat.com/profiles/rh/themes/redhatdotcom/"}'
wsk -i activation list
```
## Trigger URL to fire from a Gluster webhook

The details of setting up the Gluster webhook are out of scope for
this, but you can see the trigger URL by adding the `-v` parameter to
the `wsk -i trigger fire` command. That will show you both the
authorization HTTP header needed as well as the URL to POST to.

The general trigger URL format is `https://<openwhisk apihost>/api/v1/namespaces/_/triggers/ayersTrigger`

For example:

```
wsk -i -v trigger fire ayers-trigger -p swiftObj '{"container": "img", "method": "PUT", "object": "logo.png", "token": 
"BOGUS", "url": "https://www.redhat.com/profiles/rh/themes/redhatdotcom"}'
REQUEST:
[POST]	https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/ayersTrigger
Req Headers
{
  "Authorization": [
    "Basic <base64 encoded auth shows here>"
  ],
  "Content-Type": [
    "application/json"
  ],
  "User-Agent": [
    "OpenWhisk-CLI/1.0 (2018-03-09T02:23:55.750+0000)"
  ]
}
...
```

As another example, here is a trigger url for the ayersTrigger deployed on AWS:

```bash
https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/ayersTrigger
```

## Connecting the Gluster web hook

1. You need the trigger url (from above)
2. Take that URL along with your OW credentials and register the webhook with the bucket as follows:
```bash
curl -i -X PUT  -H "X-Auth-Token: BOGUS" \
 -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/ayersTrigger" \
 -H "X-Webhook-Auth: ODg0MmNlNGEtNjU4NS00YzkyLWI2YjMtYmQ5ZmFhYjMyNmEyOlY5MWJrNzhQMDlPNEdScWFqNGRNSm9WY0ZxM0paZEx0TVNiVkRqUEhvRDUyOU1udFZkWlVWbUpMeXVnSEtnUDk=" \
 http://storage-aws1.sysdeseng.com:8080/v1/AUTH_gv0/image-aws
```
In the previous url change the bucket name and url and the web hook url.
Repeat the operation for each bucket.


   