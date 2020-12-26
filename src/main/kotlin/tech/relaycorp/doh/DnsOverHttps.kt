package tech.relaycorp.doh

public class DnsOverHttps(public val resolverURL: String = DEFAULT_RESOLVER_URL) {
    public companion object {
        public const val DEFAULT_RESOLVER_URL: String = "https://cloudflare-dns.com/dns-query"
    }
}
