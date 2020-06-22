#!/usr/bin/bash
echo "clear the logs"
rm log/sba-connector-test.log

echo "resetting the runtime"
docker-compose down -v
docker-compose up -d schema-registry

echo "waiting for schema registry up"
sleep 10

echo "running our topology"
clojure -C:test -m prod-app.core &
echo "registering external schemas"
echo '{"schema": ""}' | jq --rawfile foo resources/schemas/external-trigger.json '. |= {schema: $foo, schemaType: "AVRO"}' | curl -X POST -H "Content-Type: application/json" http://localhost:8081/subjects/external-trigger-1-value/versions -d @-
echo '{"schema": ""}' | jq --rawfile foo resources/schemas/external-loan-application.json '. |= {schema: $foo, schemaType: "AVRO"}' | curl -X POST -H "Content-Type: application/json" http://localhost:8081/subjects/external-loan-application-1-value/versions -d @-


echo "producing external loan application"
{
  eval docker-compose exec -T schema-registry kafka-avro-console-producer \
       --bootstrap-server broker:29092 \
       --topic external-loan-application-1 \
       --property value.schema=\'$(cat resources/schemas/external-loan-application.json)\' \
       --property parse.key=true \
       --property key.schema=\'$(echo '{"type":"string"}')\' \
       --property key.separator=\" \" \
       <<< '"Y8vcTAKn2Hx5K3" {"opportunity_id": {"string": "Y8vcTAKn2Hx5K3"}, "requested_amount": {"string": "1"}, "loan_application_id": {"string": "0b62206c-d244-4a4f-80ce-daa164934b53"}, "tax_id": {"string": "6126019115"}}' 
} &> /dev/null # supresess the super verbose utility

echo "producing a trigger"
{
  eval docker-compose exec -T schema-registry kafka-avro-console-producer \
    --bootstrap-server broker:29092 \
    --topic external-trigger-1 \
    --property value.schema=\'$(cat resources/schemas/external-trigger.json)\' \
    --property parse.key=true \
    --property key.schema=\'$(echo '{"type":"string"}')\' \
    --property key.separator=\" \" \
    <<< '"Y8vcTAKn2Hx5K3" {"opportunity_id": {"string": "Y8vcTAKn2Hx5K3"}, "trigger_id": {"string": "7d7c6ca4-71e0-4228-a231-6a5449a864e7"}}' 
} &> /dev/null # surpreses the super verbose utility
echo "Wait for records to be processed"
sleep 10
pkill -P $$
echo "Done"
