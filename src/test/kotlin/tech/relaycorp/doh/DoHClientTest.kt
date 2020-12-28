package tech.relaycorp.doh

import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.verify
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Flags
import org.xbill.DNS.InvalidTypeException
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Rcode
import org.xbill.DNS.Section
import org.xbill.DNS.Type
import org.xbill.DNS.WireParseException
import java.io.IOException
import java.net.InetAddress
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DoHClientTest {
    @Nested
    inner class Constructor {
        @Test
        fun `CloudFlare DoH resolver should be used by default`() {
            val doh = DoHClient()

            assertEquals("https://cloudflare-dns.com/dns-query", doh.resolverURL)
        }

        @Test
        fun `Custom resolver URL should be allowed`() {
            val customResolverURL = "https://example.com/dns-query"
            val doh = DoHClient(customResolverURL)

            assertEquals(customResolverURL, doh.resolverURL)
        }
    }

    // https://github.com/dnsjava/dnsjava/issues/146
    @Suppress("BlockingMethodInNonBlockingContext")
    @Nested
    inner class Lookup {
        private val recordName = "example.com."
        private val recordType = "A"
        private val recordData = "192.168.1.1"

        private val dummyResponseMessage = Message()

        init {
            val dnsName = Name(recordName)
            dummyResponseMessage.addRecord(
                ARecord(dnsName, DClass.IN, 1, InetAddress.getByName(recordData)),
                Section.ANSWER
            )
        }

        @Test
        fun `Request Content-Type should be application dns-message`() = runBlockingTest {
            var contentType: String? = null
            val client = makeTestClient { request ->
                contentType = request.body.contentType.toString()
                respondDNSMessage(dummyResponseMessage)
            }

            client.lookUp(recordName, recordType)

            assertEquals("application/dns-message", contentType)
        }

        @Test
        fun `Request Accept should be application dns-message`() = runBlockingTest {
            var accept: String? = null
            val client = makeTestClient { request ->
                accept = request.headers["Accept"]
                respondDNSMessage(dummyResponseMessage)
            }

            client.lookUp(recordName, recordType)

            assertEquals("application/dns-message", accept)
        }

        @Test
        fun `Request method should be POST`() = runBlockingTest {
            var method: HttpMethod? = null
            val client = makeTestClient { request ->
                method = request.method
                respondDNSMessage(dummyResponseMessage)
            }

            client.lookUp(recordName, recordType)

            assertEquals(HttpMethod.Post, method)
        }

        @Test
        fun `Request should be made to the specified DoH endpoint`() = runBlockingTest {
            var url: String? = null
            val client = makeTestClient { request ->
                url = request.url.toString()
                respondDNSMessage(dummyResponseMessage)
            }

            client.lookUp(recordName, recordType)

            assertEquals(DoHClient.DEFAULT_RESOLVER_URL, url)
        }

        @Test
        fun `Non-200 responses should be treated as errors`() = runBlockingTest {
            val client = makeTestClient {
                respond("Whoops", HttpStatusCode.BadRequest)
            }

            val exception =
                assertThrows<LookupFailureException> { client.lookUp(recordName, recordType) }

            assertEquals(
                "Unexpected HTTP response code (${HttpStatusCode.BadRequest})",
                exception.message
            )
        }

        @Test
        fun `Connection errors should be wrapped`() = runBlocking {
            val client = makeTestClient {
                throw IOException("foo")
            }

            val exception =
                assertThrows<LookupFailureException> { client.lookUp(recordName, recordType) }

            assertEquals(
                "Failed to connect to ${DoHClient.DEFAULT_RESOLVER_URL}",
                exception.message
            )
            assertTrue(exception.cause is IOException)
        }

        @Nested
        inner class Query {
            @Test
            fun `Specified domain name should be looked up`() = runBlockingTest {
                var requestBody: ByteArray? = null
                val client = makeTestClient { request ->
                    assertTrue(request.body is OutgoingContent.ByteArrayContent)
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respondDNSMessage(dummyResponseMessage)
                }

                client.lookUp(recordName, recordType)

                assertNotNull(requestBody)
                val query = Message(requestBody!!)
                assertEquals(recordName, query.question.name.toString())
            }

            @Test
            fun `A trailing dot should be added to names without one`() = runBlockingTest {
                assertTrue(recordName.endsWith('.')) // Ensure fixture is suitable

                var requestBody: ByteArray? = null
                val client = makeTestClient { request ->
                    assertTrue(request.body is OutgoingContent.ByteArrayContent)
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respondDNSMessage(dummyResponseMessage)
                }

                client.lookUp(recordName.trimEnd('.'), recordType)

                assertNotNull(requestBody)
                val query = Message(requestBody!!)
                assertEquals(recordName, query.question.name.toString())
            }

            @Test
            fun `Specified record type should be looked up`() = runBlockingTest {
                var requestBody: ByteArray? = null
                val client = makeTestClient { request ->
                    assertTrue(request.body is OutgoingContent.ByteArrayContent)
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respondDNSMessage(dummyResponseMessage)
                }

                client.lookUp(recordName, recordType)

                assertNotNull(requestBody)
                val query = Message(requestBody!!)
                assertEquals(recordType, Type.string(query.question.rRsetType))
            }

            @Test
            fun `Invalid record types should be refused`() = runBlockingTest {
                val client = makeTestClient {
                    respondDNSMessage(dummyResponseMessage)
                }
                val invalidType = "FOOBAR"

                val exception =
                    assertThrows<InvalidQueryException> { client.lookUp(recordName, invalidType) }

                assertEquals("Invalid record type '$invalidType'", exception.message)
                assertTrue(exception.cause is InvalidTypeException)
            }

            @Test
            fun `DNS Class should be Internet`() = runBlockingTest {
                var requestBody: ByteArray? = null
                val client = makeTestClient { request ->
                    assertTrue(request.body is OutgoingContent.ByteArrayContent)
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respondDNSMessage(dummyResponseMessage)
                }

                client.lookUp(recordName, recordType)

                assertNotNull(requestBody)
                val query = Message(requestBody!!)
                assertEquals(DClass.IN, query.question.dClass)
            }

            @Test
            fun `DNSSEC verification should be requested`() = runBlockingTest {
                var requestBody: ByteArray? = null
                val client = makeTestClient { request ->
                    assertTrue(request.body is OutgoingContent.ByteArrayContent)
                    requestBody = (request.body as OutgoingContent.ByteArrayContent).bytes()
                    respondDNSMessage(dummyResponseMessage)
                }

                client.lookUp(recordName, recordType)

                assertNotNull(requestBody)
                val query = Message(requestBody!!)
                assertFalse(query.header.getFlag(Flags.CD.toInt()))
            }
        }

        @Nested
        inner class Answer {
            @Test
            fun `An exception should be thrown if the lookup failed`() = runBlockingTest {
                val responseMessage = Message()
                responseMessage.header.rcode = Rcode.SERVFAIL
                val client = makeTestClient {
                    respondDNSMessage(responseMessage)
                }

                val exception =
                    assertThrows<LookupFailureException> { client.lookUp(recordName, recordType) }

                assertEquals("Lookup failed with SERVFAIL error", exception.message)
                assertNull(exception.cause)
            }

            @Test
            fun `Exception should be thrown if response is malformed`() = runBlockingTest {
                val client = makeTestClient {
                    respond(ByteArray(0))
                }

                val exception =
                    assertThrows<LookupFailureException> { client.lookUp(recordName, recordType) }

                assertEquals("Returned DNS message is malformed", exception.message)
                assertTrue(exception.cause is WireParseException)
            }

            @Test
            fun `Exception should be thrown if data is empty`() = runBlockingTest {
                val responseMessage = Message()
                val client = makeTestClient {
                    respondDNSMessage(responseMessage)
                }

                val exception =
                    assertThrows<LookupFailureException> { client.lookUp(recordName, recordType) }

                assertEquals("Answer data is empty", exception.message)
            }

            @Test
            fun `One datum should be output if there is only one`() = runBlockingTest {
                val client = makeTestClient {
                    respondDNSMessage(dummyResponseMessage)
                }

                val answer = client.lookUp(recordName, recordType)

                assertEquals(listOf(recordData), answer.data)
            }

            @Test
            fun `Two data items should be output if there are two`() = runBlockingTest {
                val responseMessage = dummyResponseMessage.clone()
                val additionalRecordData = "1.1.1.1"
                val additionalRecordAddress = InetAddress.getByName(additionalRecordData)
                responseMessage.addRecord(
                    ARecord(Name(recordName), DClass.IN, 1, additionalRecordAddress),
                    Section.ANSWER
                )

                val client = makeTestClient {
                    respondDNSMessage(responseMessage)
                }

                val answer = client.lookUp(recordName, recordType)

                assertEquals(listOf(recordData, additionalRecordData), answer.data)
            }
        }

        private fun MockRequestHandleScope.respondDNSMessage(message: Message): HttpResponseData {
            return respond(message.toWire())
        }

        private fun makeTestClient(handler: MockRequestHandler): DoHClient {
            val dohClient = DoHClient()
            dohClient.ktorClient = HttpClient(MockEngine) {
                engine {
                    addHandler(handler)
                }
            }
            return dohClient
        }
    }

    @Test
    fun `Close method should close the underlying KTor client`() {
        val client = DoHClient()
        client.ktorClient = spy(client.ktorClient)

        client.close()

        verify(client.ktorClient).close()
    }
}
