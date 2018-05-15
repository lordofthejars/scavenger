# Instructions to create the buckets and the web hooks

```bash
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aws1.sysdeseng.com:8080/v1/AUTH_gv0/images-aws1
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aws2.sysdeseng.com:8080/v1/AUTH_gv0/images-aws2
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aws3.sysdeseng.com:8080/v1/AUTH_gv0/images-aws3

curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"           http://storage-gce1.summit-gce.sysdeseng.com:8080/v1/AUTH_gv0/images-gce1
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"        http://storage-gce2.summit-gce.sysdeseng.com:8080/v1/AUTH_gv0/images-gce2
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-gce3.summit-gce.sysdeseng.com:8080/v1/AUTH_gv0/images-gce3

curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aze1.westeurope.cloudapp.azure.com:8080/v1/AUTH_gv0/images-azr1
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aze2.westeurope.cloudapp.azure.com:8080/v1/AUTH_gv0/images-azr2
curl -i -X PUT  -H "X-Auth-Token: BOGUS"  -H "X-Webhook: https://openwhisk-openwhisk.apps.summit-aws.sysdeseng.com/api/v1/namespaces/_/triggers/functionBTrigger"         -H "X-Webhook-Auth: 7c728854-09ca-4c2f-9593-5209dc36779a:LSRQHwF2cfA3bTyQ1tXkuCUCtqGmqwZEux8gcV6eXIZd6LIOFnNxNoKZqzSPFTpc"         http://storage-aze3.westeurope.cloudapp.azure.com:8080/v1/AUTH_gv0/images-azr3



curl -v -X PUT         -H "X-Auth-Token: BOGUS"         -T bye4        http://storage-aws1.sysdeseng.com:8080/v1/AUTH_gv0/images_aws1/bye4
curl -v -X PUT         -H "X-Auth-Token: BOGUS"         -T bye4        http://storage-aws1.sysdeseng.com:8080/v1/AUTH_gv0/images_aws2/bye4
curl -v -X PUT         -H "X-Auth-Token: BOGUS"         -T bye4        http://storage-aws1.sysdeseng.com:8080/v1/AUTH_gv0/images_aws3/bye4

```
