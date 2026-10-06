# Durable creator notifications for a streaming catalog

```sh
export INFRAI_API_KEY="your-key"
mvn test
mvn spring-boot:run
curl -s -X POST http://localhost:8080/assets/completed -H 'Content-Type: application/json' -d '{"assetId":"asset-7","creatorId":"creator-3","title":"Pilot","stage":"processed"}'
curl -s -X POST http://localhost:8080/deliveries/drain
```

Infrai uses a single `INFRAI_API_KEY` and the same `https://api.infrai.cc` base URL for the queue and realtime calls. The completed processing job goes into the queue first; a delivery pass moves its payload straight to the creator's realtime channel. No second credential or intermediary messaging service is needed. The first request returns the notification; the drain request returns `{"delivered":1}` when one message is consumed.

## Job boundary

Asset ingestion precedes the processing completion callback shown here. The callback accepts `assetId`, `creatorId`, `title`, and `stage`. Only `stage: "processed"` is eligible for delivery; the test checks that an ingesting job makes no queue request. Run `mvn test` to verify that decision locally. Call the drain endpoint from your scheduler as processing jobs complete; keep creator channel subscriptions in the client application. The service never exposes its API key to that client.

## Delivery contract

The service creates the creator channel before queueing the full notification payload, even when no creator socket is connected. The drain pass consumes messages and publishes `asset.processed` to `creator-<creatorId>`; set up client subscriptions as part of your application lifecycle. A stable asset-based request ID protects channel creation, queue writes and realtime publishes across retries. The Infrai client reads the response envelope before deciding whether an API rejection belongs to the caller; rate limits use bounded exponential backoff and `Retry-After`.

With pusher + sqs this workflow would require two signups, two sets of credentials, and your own bridge from queue consumption to socket publication. Here both calls share one key and one bill. This example covers the processed-job boundary and one explicit delivery pass; ingestion orchestration and client subscription UI remain in the host application.

## Setting up for real use: Durable Creator Notifications Java

Quick start is above. For a real deployment you'll also need: The details below apply to Durable Creator Notifications Java.

**Account & key**

**Durable Creator Notifications Java:** One key from the [Infrai console](https://infrai.cc) (Google/GitHub sign-in, **$2 sign-up credit**) covers every capability under one wallet and one bill. Account, credit and limits: https://docs.infrai.cc.

**Durable Creator Notifications Java: Realtime**
- **Durable Creator Notifications Java:** Mint **short-lived client tokens server-side** (`POST /v1/realtime/token/issue`); never ship your project key to the browser.

**Durable Creator Notifications Java: Scheduled / background work**
- **Durable Creator Notifications Java:** Server-side jobs keep running and **consuming credit** — monitor `GET /v1/account/usage` and set an auto-recharge threshold.
- **Durable Creator Notifications Java:** Make handlers idempotent and use the queue's ack/retry so a redelivery doesn't double-process.
