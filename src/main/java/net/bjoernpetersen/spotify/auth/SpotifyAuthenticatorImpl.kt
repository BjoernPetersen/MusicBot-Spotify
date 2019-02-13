package net.bjoernpetersen.spotify.auth

import com.google.api.client.auth.oauth2.BrowserClientRequestUrl
import mu.KotlinLogging
import net.bjoernpetersen.musicbot.api.config.ActionButton
import net.bjoernpetersen.musicbot.api.config.Config
import net.bjoernpetersen.musicbot.api.config.ConfigSerializer
import net.bjoernpetersen.musicbot.api.config.IntSerializer
import net.bjoernpetersen.musicbot.api.config.NonnullConfigChecker
import net.bjoernpetersen.musicbot.api.config.NumberBox
import net.bjoernpetersen.musicbot.api.config.PasswordBox
import net.bjoernpetersen.musicbot.api.config.SerializationException
import net.bjoernpetersen.musicbot.spi.plugin.management.InitStateWriter
import net.bjoernpetersen.musicbot.spi.util.BrowserOpener
import java.io.IOException
import java.net.MalformedURLException
import java.net.URL
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Arrays
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject

class SpotifyAuthenticatorImpl : SpotifyAuthenticator {

    private val logger = KotlinLogging.logger { }
    private val lock: Lock = ReentrantLock()
    private val random = SecureRandom()

    override val name: String = "Spotify Auth"
    override val token: String
        get() = currentToken!!.value

    @Inject
    private lateinit var browserOpener: BrowserOpener

    private lateinit var port: Config.SerializedEntry<Int>
    private lateinit var clientId: Config.StringEntry

    private lateinit var tokenExpiration: Config.SerializedEntry<Instant>
    private lateinit var accessToken: Config.StringEntry

    private var currentToken: Token? = null
        get() {
            val current = field
            if (current == null) {
                field = initAuth()
            } else if (current.isExpired()) {
                field = authorize()
            }
            return field
        }
        set(value) {
            field = value
            tokenExpiration.set(value?.expiration)
            accessToken.set(value?.value)
        }

    @Throws(IOException::class, InterruptedException::class)
    private fun initAuth(): Token {
        if (accessToken.get() != null && tokenExpiration.get() != null) {
            val token = accessToken.get()!!
            val expirationDate = tokenExpiration.get()!!
            val result = Token(token, expirationDate)
            // if it's expired, this call will refresh the token
            if (!result.isExpired()) return result
        }
        return authorize()
    }

    private fun getSpotifyUrl(state: String, redirectUrl: URL): URL {
        try {
            return URL(BrowserClientRequestUrl(SPOTIFY_URL, clientId.get()!!)
                .setState(state)
                .setScopes(Arrays.asList("user-modify-playback-state", "user-read-playback-state"))
                .setRedirectUri(redirectUrl.toExternalForm())
                .build())
        } catch (e: MalformedURLException) {
            throw IllegalArgumentException(e)
        }
    }

    private fun generateRandomString(): String {
        return Integer.toString(random.nextInt(Integer.MAX_VALUE))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun authorize(): Token {
        logger.debug("Acquiring auth lock...")
        if (!lock.tryLock(10, TimeUnit.SECONDS)) {
            logger.warn("Can't acquire Auth lock!")
            throw InterruptedException()
        }
        try {
            logger.debug("Lock acquired")

            val state = generateRandomString()
            val receiver = LocalServerReceiver(port.get()!!, state)
            val redirectUrl = receiver.redirectUrl

            val url = getSpotifyUrl(state, redirectUrl)
            browserOpener.openDocument(url)

            try {
                val token = receiver.waitForToken(1, TimeUnit.MINUTES)
                    ?: throw IOException("Received null token.")

                accessToken.set(token.value)
                tokenExpiration.set(token.expiration)

                return token
            } catch (e: InterruptedException) {
                throw IOException("Not authenticated within 1 minute.", e)
            }
        } finally {
            lock.unlock()
        }
    }

    override fun initialize(initStateWriter: InitStateWriter) {
        initStateWriter.state("Retrieving token...")
        token.also { initStateWriter.state("Retrieved token.") }
    }

    override fun createConfigEntries(config: Config): List<Config.Entry<*>> = listOf(
        config.SerializedEntry(
            "port",
            "OAuth callback port",
            IntSerializer,
            NonnullConfigChecker,
            NumberBox(1024, 65535),
            58642).also { port = it })

    override fun createSecretEntries(secrets: Config): List<Config.Entry<*>> {
        tokenExpiration = secrets.SerializedEntry(
            "tokenExpiration",
            "Token expiration date",
            InstantSerializer,
            NonnullConfigChecker,
            ActionButton("Refresh", ::toTimeString) {
                try {
                    val token = authorize()
                    this.currentToken = token
                    true
                } catch (e: Exception) {
                    false
                }
            })

        accessToken = secrets.StringEntry(
            "accessToken",
            "OAuth access token",
            { null },
            null)

        clientId = secrets.StringEntry(
            key = "clientId",
            description = "OAuth client ID. Only required if there is a custom port.",
            configChecker = { null },
            uiNode = PasswordBox,
            default = CLIENT_ID)

        return listOf(tokenExpiration, clientId)
    }

    override fun createStateEntries(state: Config) {}

    override fun close() {
    }

    private companion object {
        private const val SPOTIFY_URL = " https://accounts.spotify.com/authorize"
        private const val CLIENT_ID = "902fe6b9a4b6421caf88ee01e809939a"

        private fun toTimeString(instant: Instant) = DateTimeFormatter
            .ofLocalizedTime(FormatStyle.SHORT)
            .format(ZonedDateTime.ofInstant(instant, ZoneId.systemDefault()))
    }
}

private object InstantSerializer : ConfigSerializer<Instant> {
    @Throws(SerializationException::class)
    override fun deserialize(string: String): Instant {
        return string.toLongOrNull()?.let(Instant::ofEpochSecond) ?: throw SerializationException()
    }

    override fun serialize(obj: Instant): String = obj.epochSecond.toString()
}