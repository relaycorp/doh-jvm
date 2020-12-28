import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.doh.DoHClient
import tech.relaycorp.doh.LookupFailureException
import kotlin.test.assertEquals

class DoHTest {
    private val client = DoHClient()

    @AfterAll
    fun cleanUp() {
        client.close()
    }

    @Test
    fun validDomain() = runBlocking {
        val ipAddress = "4.4.8.8"

        val message = client.lookup("$ipAddress.in-addr.arpa.", "PTR")

        assertEquals("dns.google.", message.data.first())
    }

    @Test
    fun trailingDot() = runBlocking {
        val answerWithoutDot = client.lookup("example.com", "A")
        val answerWithDot = client.lookup("example.com.", "A")

        assertEquals(answerWithDot, answerWithoutDot)
    }

    @Test
    fun googleDOH() = runBlocking {
        val gClient = DoHClient("https://dns.google/dns-query")
        gClient.use {
            val gMessage = gClient.lookup("example.com.", "A")
            val cfMessage = client.lookup("example.com.", "A")

            assertEquals(cfMessage, gMessage)
        }
    }

    @Test
    fun invalidDNSSEC(): Unit = runBlocking {
        assertThrows<LookupFailureException> { client.lookup("dnssec-failed.org.", "A") }
    }

    @Test
    fun validDNSSEC(): Unit = runBlocking {
        client.lookup("dnssec-deployment.org.", "A")
    }

    @Test
    fun http400(): Unit = runBlocking {
        DoHClient("https://httpstat.us/400").use {
            assertThrows<LookupFailureException> { it.lookup("example.com", "A") }
        }
    }
}
