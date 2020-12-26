package tech.relaycorp.doh

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DnsOverHttpsTest {
    @Nested
    inner class Constructor {
        @Test
        fun `CloudFlare DoH resolver should be used by default`() {
            val doh = DnsOverHttps()

            assertEquals("https://cloudflare-dns.com/dns-query", doh.resolverURL)
        }

        @Test
        fun `Custom resolver URL should be allowed`() {
            val customResolverURL = "https://example.com/dns-query"
            val doh = DnsOverHttps(customResolverURL)

            assertEquals(customResolverURL, doh.resolverURL)
        }

        @Test
        @Disabled
        fun `Default KTor client should be used by default`() {
        }

        @Test
        @Disabled
        fun `Custom KTor client can be specified`() {
        }
    }

    @Nested
    inner class Lookup {
        @Test
        @Disabled
        fun `Specified domain name should be looked up`() {
        }

        @Test
        @Disabled
        fun `Specified record type should be looked up`() {
        }

        @Test
        @Disabled
        fun `DNSSEC verification should be requested by default`() {
        }

        @Test
        @Disabled
        fun `DNSSEC can be disabled if requested`() {
        }
    }
}
