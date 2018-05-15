# Updating the buckets

This page explains how to update the bucket.

## prerequisites

You need:

1. the current project
2. Apache Maven
3. The `oc` client and be on the right OpenShift project (scavenger-hunt-microservice):

```bash
oc project scavenger-hunt-microservice
```

## Edit the conf file

Edit the `openshift/_provider_/config.json` file:

```json
{
  "data-center": "Amazon",
  "locations": {
    "localhost": {
      "s3-enable": false,
      "jdg-enable": false,
      "fake-score": true,
      "sso-auth-enable": false
    },
    "Amazon": {
      "s3-enable": true,
      "s3-urls": [ 
        "http://storage-aws5.sysdeseng.com:8080/v1/AUTH_gv1/images-aws1", // HERE
        "http://storage-aws5.sysdeseng.com:8080/v1/AUTH_gv1/images-aws2", // HERE
        "http://storage-aws5.sysdeseng.com:8080/v1/AUTH_gv1/images-aws3"  // HERE
      ],
      "s3-auth-token": "BOGUS",
      "jdg-enable": true,
      "jdg-url": "jdg-app-hotrod.datagrid.svc",
      "fake-score": false,

      "sso-enable": true,
      "sso-cert-url": "https://secure-sso-sso.apps.summit-aws.sysdeseng.com/auth/realms/summit/protocol/openid-connect/certs",
     
      "leader-board-url": "http://score-gateway.scavenger-hunt-microservice.svc:8080/rank"

    }
  }
  // ...
}
```

**IMPORTANT**: edit the right file. The configuration depends on the cloud provider: `aws`, `azr` and `gce`.

## Push the change

1. Be sure to be connected to the **right** cluster
2. If not already done: `mvn clean dependency:copy-dependencies compile -DincludeScope=runtime`
3. Run `./update.sh`

