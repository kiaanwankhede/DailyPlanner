package app.roz.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class Settings(
    val morningMinute: Int = 8 * 60,
    val eveningMinute: Int = 20 * 60 + 30,
    /** The item marked as top priority, if any. */
    val focus: FocusPick? = null,
    /** Show only "Top priority" (not what it is) when the phone is locked. */
    val hideFocusOnLock: Boolean = false,
    /** The weekly "add one recurring item" prompt. */
    val addPromptOn: Boolean = true,
    /** The day the weekly count starts from; null means 1 January of the current year. */
    val addPromptStart: Day? = null,
)

@Serializable
data class HabitLog(val habitId: Long, val day: HabitDay)

/** Everything a backup holds. Bump [CURRENT_VERSION] when the shape changes, and keep reading old ones. */
@Serializable
data class BackupPayload(
    val version: Int = CURRENT_VERSION,
    val createdAt: DateTime,
    val habits: List<Habit> = emptyList(),
    val habitLog: List<HabitLog> = emptyList(),
    val chores: List<Chore> = emptyList(),
    val choreLog: List<ChoreLogEntry> = emptyList(),
    val reminders: List<Reminder> = emptyList(),
    val people: List<Person> = emptyList(),
    val settings: Settings = Settings(),
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

/** "Backup from 12 Oct: 6 habits, 14 chores, 24 birthdays". */
fun BackupPayload.preview(): String {
    fun n(count: Int, word: String) = "$count $word${if (count == 1) "" else "s"}"
    val date = createdAt.format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", java.util.Locale.ENGLISH))
    return "Backup from $date: " + listOf(
        n(habits.size, "habit"),
        n(chores.size, "chore"),
        n(reminders.size, "reminder"),
        n(people.size, "birthday"),
    ).joinToString(", ")
}

class WrongPassphraseException : Exception("Neither the passphrase nor the recovery code matches this backup")

class NotABackupException : Exception("This file is not a Roz backup")

class NewerBackupException(version: Int) : Exception("This backup was made by a newer version of Roz (format $version)")

/**
 * Backup file format (SPEC.md §7):
 *
 * `"ROZ1" | format (1) | saltP (16) | ivP (12) | keyP (48) | saltR (16) | ivR (12) | keyR (48) | iv (12) | data`
 *
 * A random 256-bit data key encrypts gzip(JSON) with AES-256-GCM. That data key is stored twice,
 * sealed once with a key derived from the passphrase and once with a key derived from the
 * recovery code (PBKDF2-SHA256), so either one opens the file.
 */
object BackupCodec {
    private val MAGIC = "ROZ1".toByteArray(Charsets.US_ASCII)
    private const val FORMAT: Byte = 1
    private const val SALT = 16
    private const val IV = 12
    private const val KEY = 32
    private const val TAG_BITS = 128
    private const val SEALED_KEY = KEY + TAG_BITS / 8
    private const val ITERATIONS = 210_000
    private const val HEADER = 4 + 1 + 2 * (SALT + IV + SEALED_KEY) + IV

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val random = SecureRandom()

    fun encode(payload: BackupPayload, passphrase: CharArray, recoveryCode: CharArray): ByteArray {
        val dataKey = ByteArray(KEY).also(random::nextBytes)
        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        out.write(FORMAT.toInt())
        for (secret in listOf(passphrase, RecoveryCode.normalize(recoveryCode))) {
            val salt = ByteArray(SALT).also(random::nextBytes)
            val iv = ByteArray(IV).also(random::nextBytes)
            out.write(salt)
            out.write(iv)
            out.write(seal(derive(secret, salt), iv, dataKey))
        }
        val iv = ByteArray(IV).also(random::nextBytes)
        out.write(iv)
        out.write(seal(dataKey, iv, gzip(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())))
        return out.toByteArray()
    }

    /** Opens a backup with either the passphrase or the recovery code. */
    fun decode(bytes: ByteArray, secret: CharArray): BackupPayload {
        if (bytes.size < HEADER + TAG_BITS / 8 || !bytes.copyOfRange(0, 4).contentEquals(MAGIC)) throw NotABackupException()
        val buf = ByteBuffer.wrap(bytes, 5, bytes.size - 5)
        val slots = List(2) {
            Triple(ByteArray(SALT).also(buf::get), ByteArray(IV).also(buf::get), ByteArray(SEALED_KEY).also(buf::get))
        }
        val iv = ByteArray(IV).also(buf::get)
        val data = ByteArray(buf.remaining()).also(buf::get)
        val candidates = listOf(secret to slots[0], RecoveryCode.normalize(secret) to slots[1])
        val dataKey = candidates.firstNotNullOfOrNull { (s, slot) ->
            runCatching { open(derive(s, slot.first), slot.second, slot.third) }.getOrNull()
        } ?: throw WrongPassphraseException()
        val text = gunzip(open(dataKey, iv, data)).toString(Charsets.UTF_8)
        val version = json.parseToJsonElement(text).let { (it as? kotlinx.serialization.json.JsonObject)?.get("version") }
            ?.toString()?.toIntOrNull() ?: 1
        if (version > BackupPayload.CURRENT_VERSION) throw NewerBackupException(version)
        return json.decodeFromString(BackupPayload.serializer(), text)
    }

    private fun seal(key: ByteArray, iv: ByteArray, plain: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(MAGIC)
            doFinal(plain)
        }

    private fun open(key: ByteArray, iv: ByteArray, sealed: ByteArray): ByteArray =
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            updateAAD(MAGIC)
            try {
                doFinal(sealed)
            } catch (e: AEADBadTagException) {
                throw WrongPassphraseException()
            }
        }

    private fun derive(secret: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(secret, salt, ITERATIONS, KEY * 8)
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(data: ByteArray): ByteArray =
        ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(data) } }.toByteArray()

    private fun gunzip(data: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
}

/** The 20-character recovery code shown once at setup, e.g. `7KQ2M-X9D4T-PW3HC-B6RNA`. */
object RecoveryCode {
    // Crockford base32: no I, L, O or U, so it is hard to misread.
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    private val random = SecureRandom()

    fun generate(): String = (1..20).map { ALPHABET[random.nextInt(ALPHABET.length)] }
        .chunked(5).joinToString("-") { it.joinToString("") }

    /** Accepts lower case, spaces, dashes and the usual look-alikes (O→0, I/L→1). */
    fun normalize(input: CharArray): CharArray = String(input).uppercase()
        .filter { !it.isWhitespace() && it != '-' }
        .map { when (it) { 'O' -> '0'; 'I', 'L' -> '1'; else -> it } }
        .toCharArray()

    fun looksValid(input: String): Boolean = normalize(input.toCharArray()).let { c ->
        c.size == 20 && c.all { it in ALPHABET }
    }
}

/** Which backup files to keep: the newest [keepLatest], plus the newest one of each month (SPEC.md §7). */
object BackupRetention {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmm")
    private val pattern = Regex("""roz-backup-(\d{4}-\d{2}-\d{2}-\d{4})\.rozb""")

    fun fileName(at: LocalDateTime): String = "roz-backup-${at.format(stamp)}.rozb"

    fun parse(name: String): LocalDateTime? =
        pattern.matchEntire(name)?.let { runCatching { LocalDateTime.parse(it.groupValues[1], stamp) }.getOrNull() }

    /** Names of files to delete. Files that don't look like Roz backups are never touched. */
    fun toDelete(names: Collection<String>, keepLatest: Int = 7): List<String> {
        val dated = names.mapNotNull { n -> parse(n)?.let { n to it } }.sortedByDescending { it.second }
        val keep = dated.take(keepLatest).map { it.first }.toMutableSet()
        dated.groupBy { YearMonth.from(it.second) }.values.forEach { keep += it.first().first }
        return dated.map { it.first }.filter { it !in keep }
    }
}

object BackupHealth {
    const val STALE_AFTER_DAYS = 3L

    fun isStale(lastSuccess: Instant?, now: Instant): Boolean =
        lastSuccess == null || Duration.between(lastSuccess, now).toDays() >= STALE_AFTER_DAYS

    /** Ask monthly whether the passphrase is still known. */
    fun passphraseCheckDue(lastCheck: LocalDate?, today: LocalDate): Boolean =
        lastCheck == null || !lastCheck.plusDays(30).isAfter(today)
}
