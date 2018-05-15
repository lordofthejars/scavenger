#!/usr/bin/env bash
TOKEN=$1
http https://api.github.com/user/repos "Authorization:token $TOKEN" per_page==100 type==owner | jq '.[].full_name' | xargs -I '{}' http DELETE https://api.github.com/repos/'{}' "Authorization:token $TOKEN"