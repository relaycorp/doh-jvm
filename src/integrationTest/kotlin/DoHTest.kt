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

        val answer = client.lookUp("$ipAddress.in-addr.arpa.", "PTR")

        assertEquals("dns.google.", answer.data.first())
    }

    @Test
    fun trailingDot() = runBlocking {
        val answerWithoutDot = client.lookUp("example.com", "A")
        val answerWithDot = client.lookUp("example.com.", "A")

        assertEquals(answerWithDot, answerWithoutDot)
    }

    @Test
    fun googleDOH() = runBlocking {
        val gClient = DoHClient("https://dns.google/dns-query")
        gClient.use {
            val gMessage = gClient.lookUp("example.com.", "A")
            val cfMessage = client.lookUp("example.com.", "A")

            assertEquals(cfMessage, gMessage)
        }
    }

    @Test
    fun invalidDNSSEC(): Unit = runBlocking {
        assertThrows<LookupFailureException> { client.lookUp("dnssec-failed.org.", "A") }
    }

    @Test
    fun validDNSSEC(): Unit = runBlocking {
        client.lookUp("dnssec-deployment.org.", "A")
    }

    @Test
    fun http400(): Unit = runBlocking {
        DoHClient("https://httpstat.us/400").use {
            assertThrows<LookupFailureException> { it.lookUp("example.com", "A") }
        }
    }
}
