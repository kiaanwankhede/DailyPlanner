package app.roz.data

import android.content.Context
import android.provider.ContactsContract
import app.roz.core.OccasionKind
import app.roz.core.Person

/** Birthdays and anniversaries saved in the phone's contacts, for the one-time import. */
object ContactBirthdays {
    fun read(context: Context): List<Person> {
        val events = ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE
        val projection = arrayOf(
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.CommonDataKinds.Event.TYPE,
        )
        val out = mutableListOf<Person>()
        context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI, projection,
            "${ContactsContract.Data.MIMETYPE} = ?", arrayOf(events), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val date = c.getString(1) ?: continue
                val kind = when (c.getInt(2)) {
                    ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> OccasionKind.BIRTHDAY
                    ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> OccasionKind.ANNIVERSARY
                    else -> continue
                }
                parse(date)?.let { (year, month, day) -> out += Person(0, name, "", kind, month, day, year) }
            }
        }
        return out.distinctBy { Triple(it.name, it.month, it.day) }
    }

    /** "1990-05-21", "--05-21" (no year) or "19900521". */
    internal fun parse(text: String): Triple<Int?, Int, Int>? {
        Regex("""^(\d{4}|-)-(\d{2})-(\d{2})""").find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull()
            return Triple(year, m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf { it.second in 1..12 && it.third in 1..31 }
        }
        Regex("""^(\d{4})(\d{2})(\d{2})$""").find(text)?.let { m ->
            return Triple(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()).takeIf { it.second in 1..12 && it.third in 1..31 }
        }
        return null
    }
}
