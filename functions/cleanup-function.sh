#!/bin/bash
set -e

source ./openwhisk-env.sh

echo "🔧 Deleting rules"
delete rule score-rule
delete rule functionBRule
delete rule sequenceRule

echo "🔧 Deleting triggers"
delete trigger object-written
delete trigger  functionBTrigger

echo "🔧 Deleting actions"
delete action infinispan-feed
delete action compute-score
delete action functionBAction
delete action fetchImage
delete action persistScore
delete action scoreImage
delete action imageSequence

echo -e "🥃  Done!"



