/*
 * Copyright (c) 2026 The Latte Project
 * SPDX-License-Identifier: MIT
 */
import { Container } from "@cloudflare/containers";
import { env } from "cloudflare:workers";

/**
 * The Latte Java app runs as a JVM HTTP server inside a Cloudflare Container (see ../../../Dockerfile,
 * which ships the `latte bundle` output and boots org.lattejava.app.Main on port 8080). This Worker is
 * pure glue: it forwards every incoming request to a single shared container instance and returns the
 * response unchanged. No application logic lives here.
 *
 * All app configuration is delivered to the JVM as environment variables. Non-secret values come from
 * the `[vars]` block in wrangler.toml; secrets come from `wrangler secret put`. Both land on `env` and
 * are mirrored into `envVars` below, which Cloudflare injects into the container at startup.
 * org.lattejava.web.Configuration reads them, normalizing dotted setting names to UPPER_SNAKE
 * (e.g. `d1.accountId` -> `D1_ACCOUNTID`), so the keys here use that normalized form.
 */
export class LatteApp extends Container {
  defaultPort = 8080;
  // Run the container 24/7 so users never pay a JVM cold start. NOTE: the @cloudflare/containers
  // `Container` base class we extend defaults `sleepAfter` to "10m" (DEFAULT_SLEEP_AFTER) and actively
  // signals the container to stop once that idle window elapses — so simply OMITTING sleepAfter does
  // NOT give the platform's "never idle-evict" behavior; it silently inherits the 10m default and the
  // container scales to zero (the "Ready" state) ~10 minutes after the last request. Cloudflare's docs
  // ("if you do not set sleepAfter, it keeps running") describe the raw platform, not this helper class.
  // The library only accepts s/m/h time expressions with no "never" sentinel, so we set an effectively
  // infinite idle window: any real request renews the timer, and the only things that stop the container
  // are a host restart or a deploy (both unavoidable). This keeps one instance running continuously
  // (a few dollars a month) without the rejected scale-to-zero + cron-warmer approach. The dominant
  // cold-start cost is JVM boot, not JTE compilation (templates compile in ms and are cached); a visitor
  // can only hit a cold start on the first request after a deploy or host restart.
  sleepAfter = "8760h"; // ~1 year — effectively "never idle-evict"
  // Port-readiness probe target. The default ("ping" -> GET /) hits the app's "/" route, which 301s to
  // /app/ and then 302s to the https FusionAuth login; the probe follows redirects and dies trying to
  // open https to the container ("HTTPS not currently supported"), so readiness never passes. Point it
  // at GET /health, which returns 200 with no redirect.
  pingEndpoint = "ping/health";

  envVars = {
    // Non-secret configuration (set in wrangler.toml [vars]).
    D1_ACCOUNTID: env.D1_ACCOUNTID,
    D1_BASEURL: env.D1_BASEURL,
    D1_DATABASEID: env.D1_DATABASEID,
    FUSIONAUTH_BASEURL: env.FUSIONAUTH_BASEURL,
    FUSIONAUTH_ISSUER: env.FUSIONAUTH_ISSUER,
    FUSIONAUTH_CLIENTID: env.FUSIONAUTH_CLIENTID,
    FUSIONAUTH_CLICLIENTID: env.FUSIONAUTH_CLICLIENTID,
    GITHUB_CLIENTID: env.GITHUB_CLIENTID,
    S3_ACCESSKEYID: env.S3_ACCESSKEYID,
    S3_BUCKET: env.S3_BUCKET,
    S3_ENDPOINT: env.S3_ENDPOINT,
    S3_REGION: env.S3_REGION,

    // Secrets (set with `wrangler secret put <NAME>`).
    D1_APITOKEN: env.D1_APITOKEN,
    FUSIONAUTH_APIKEY: env.FUSIONAUTH_APIKEY,
    FUSIONAUTH_CLIENTSECRET: env.FUSIONAUTH_CLIENTSECRET,
    FUSIONAUTH_CLICLIENTSECRET: env.FUSIONAUTH_CLICLIENTSECRET,
    GITHUB_CLIENTSECRET: env.GITHUB_CLIENTSECRET,
    S3_SECRETACCESSKEY: env.S3_SECRETACCESSKEY,
    WEB_COOKIEENCRYPTIONKEY: env.WEB_COOKIEENCRYPTIONKEY,
  };
}

export default {
  async fetch(request, env) {
    // Cloudflare terminates TLS at the edge; the container listens on plain HTTP. The JVM derives its
    // external base URL (used to build the OIDC redirect_uri and to mark session cookies Secure) from
    // the X-Forwarded-* headers, so we set them explicitly here. This Worker only ever serves the
    // https custom domain, so proto/port are constant. Without these the app would emit
    // http://app.lattejava.org:8080/oidc/return and FusionAuth would reject the login.
    const url = new URL(request.url);
    const headers = new Headers(request.headers);
    headers.set("X-Forwarded-Proto", "https");
    headers.set("X-Forwarded-Host", url.host);
    headers.set("X-Forwarded-Port", "443");
    const forwarded = new Request(request, { headers });

    // Single shared instance: all traffic routes to one container. Sufficient for a single-instance app.
    return env.LATTE_APP.getByName("default").fetch(forwarded);
  },
};
