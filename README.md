# DNS-over-HTTPS (DoH) client for the JVM and Android

This library implements a DNS-over-HTTPS (DoH) client for the JVM 8+ and Android 5+. By default, it connects to Cloudflare's DoH resolver, but any RFC 8484-compliant resolver can be used.

Behind the scenes, this client uses OkHTTP via KTor in order to get coroutine support and keep a connection pool.

For privacy reasons, HTTP requests are made with the POST method, but we'd welcome a PR to support GET requests optionally.

For security reasons, DNSSEC validation can't be turned off, but we're open to consider toggling DNSSEC validation if there's a good reason to do so and a PR is subsequently proposed.

## Install

You can get this library from JCenter:

```
implementation "tech.relaycorp:doh:1.0.0"
```

Check the [GitHub releases](https://github.com/relaycorp/doh-jvm/releases) to find the latest version.

## Usage

The client has a very simple interface. For example, to resolve the `A` record(s) for `example.com`, you'd just do:

```kotlin
var aRecords : List<String>? = null
val doh = DoHClient()
doh.use {
    aRecords = doh.lookUp("example.com", "A").data
}
```

Make sure to wrap your code around `.use { ... }` or call `.close()` after using the client in order to release the underlying resources.

If you don't wish to use the default resolver (Cloudflare), pass the DoH resolver URL to the constructor of `DoHClient`.

[Read the API documentation online](https://docs.relaycorp.tech/doh-jvm).

## Comparison with other JVM DoH clients

- [`org.xbill.DNS.DohResolver`](https://javadoc.io/doc/dnsjava/dnsjava/latest/org/xbill/DNS/DohResolver.html) is a proof-of-concept implementation with documented shortcomings that make it unsuitable for production environments.
- [`dohjava`](https://github.com/NUMtechnology/dohjava) is undocumented, seemed abandoned, and a cursory code review uncovered issues such as:
  - It creates a single-use, blocking HTTP client.
  - It only supports GET requests, which makes responses cacheable, but is [bad for privacy](https://developers.google.com/speed/public-dns/docs/doh#privacy_best_practices).
- [`okhttp3.dnsoverhttps.DnsOverHttps`](https://square.github.io/okhttp/3.x/okhttp-dnsoverhttps/okhttp3/dnsoverhttps/DnsOverHttps.html) only supports `A`/`AAAA` records because it appears to be only meant to be used with OkHTTP.

## Contributing

We love contributions! If you haven't contributed to a Relaycorp project before, please take a minute to [read our guidelines](https://github.com/relaycorp/.github/blob/master/CONTRIBUTING.md) first.
