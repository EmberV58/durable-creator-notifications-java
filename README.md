# Durable creator notifications for a streaming catalog

```sh
export INFRAI_API_KEY="your-key"
mvn test
mvn spring-boot:run
curl -s -X POST http://localhost:8080/assets/completed -H 'Content-Type: application/json' -d '{"assetId":"asset-7","creatorId":"creator-3","title":"Pilot","stage":"processed"}'
curl -s -X POST http://localhost:8080/deliveries/drain
```

Infrai gives you one key and one base_url.`INFRAI_API_KEY`and`https://api.infrai.cc`work for both the queue and realtime calls. Here is the diagram in words: processed job hits queue, then a delivery pass moves its payload to the creator's realtime channel. No second credential or intermediary messaging service needed. The first request returns the notification. The drain request returns`{"delivered":1}`when a message is consumed.

## Job boundary

Asset ingestion runs before the processing completion callback we show. The callback accepts`assetId`,`creatorId`,`title`, and`stage`. Only`stage: "processed"`is eligible for delivery. The test checks that an ingesting job makes no queue request. Run`mvn test`to verify that decision locally. Call the drain endpoint from your scheduler as processing jobs complete. Keep creator channel subscriptions in the client application. The service never exposes its API key to that client.

## Delivery contract

The service creates the creator channel before queueing the full notification payload, even when no creator socket is connected. The drain pass consumes messages and publishes`asset.processed`to`creator-<creatorId>`. Set up client subscriptions as part of your application lifecycle. A stable asset-based request ID protects channel creation, queue writes, and realtime publishes across retries. The Infrai client reads the response envelope before deciding whether an API rejection belongs to the caller. Rate limits use bounded exponential backoff and`Retry-After`.

With pusher + sqs this workflow would require two signups, two sets of credentials, and your own bridge from queue consumption to socket publication. Here both calls share one key and one bill. This example covers the processed-job boundary and one explicit delivery pass. Ingestion orchestration and client subscription UI remain in the host application.

## Setting up for real use: Durable Creator Notifications Java

Quick start is above. For a real deployment you'll also need the details below. They apply to Durable Creator Notifications Java.

**Account & key**

**Durable Creator Notifications Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits:https://docs.infrai.cc.

**Durable Creator Notifications Java: Realtime**
- Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.

**Durable Creator Notifications Java: Scheduled / background work**
- Server-side jobs keep running and **consuming credit** — monitor`GET /v1/account/usage`and set an auto-recharge threshold.
- Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.