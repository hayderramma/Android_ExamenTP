package com.example.contacts.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.contacts.data.ContactContract.Columns

/** Seule classe contenant du SQL (hors DDL du helper). */
class ContactDao(context: Context) {

    private val helper = ContactDbHelper.getInstance(context)

    fun insert(contact: Contact): Long {
        val values = toContentValues(contact)
        return helper.writableDatabase.insert(ContactContract.TABLE_NAME, null, values)
    }

    fun update(contact: Contact): Int {
        if (contact.id <= 0L) return 0
        val values = toContentValues(contact)
        return helper.writableDatabase.update(
            ContactContract.TABLE_NAME,
            values,
            "${Columns.ID} = ?",
            arrayOf(contact.id.toString())
        )
    }

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(
            ContactContract.TABLE_NAME,
            "${Columns.ID} = ?",
            arrayOf(id.toString())
        )

    fun getAll(): List<Contact> = query(null, null)

    fun getById(id: Long): Contact? {
        val cursor = helper.readableDatabase.query(
            ContactContract.TABLE_NAME,
            ContactContract.ALL_COLUMNS,
            "${Columns.ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        return cursor.use { c ->
            if (c.moveToFirst()) c.toContact() else null
        }
    }

    /**
     * Recherche SQL disponible (option "SQL" du choix de filtrage). L'UI V4 utilise
     * le filtrage en mémoire via le ViewModel, mais cette méthode reste opérationnelle
     * et démontre l'usage correct de selectionArgs + ESCAPE pour LIKE.
     */
    fun search(texte: String): List<Contact> {
        if (texte.isBlank()) return getAll()
        val like = "%${escapeLike(texte.trim())}%"
        val selection = "(" +
            "${Columns.NOM} LIKE ? ESCAPE '\\' OR " +
            "${Columns.PRENOM} LIKE ? ESCAPE '\\' OR " +
            "${Columns.TELEPHONE} LIKE ? ESCAPE '\\'" +
            ")"
        return query(selection, arrayOf(like, like, like))
    }

    private fun query(selection: String?, selectionArgs: Array<String>?): List<Contact> {
        val cursor = helper.readableDatabase.query(
            ContactContract.TABLE_NAME,
            ContactContract.ALL_COLUMNS,
            selection,
            selectionArgs,
            null,
            null,
            ContactContract.ORDER_BY
        )
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) add(c.toContact())
            }
        }
    }

    private fun Cursor.toContact(): Contact = Contact(
        id = getLong(getColumnIndexOrThrow(Columns.ID)),
        nom = getString(getColumnIndexOrThrow(Columns.NOM)),
        prenom = getString(getColumnIndexOrThrow(Columns.PRENOM)),
        telephone = getString(getColumnIndexOrThrow(Columns.TELEPHONE)),
        email = getString(getColumnIndexOrThrow(Columns.EMAIL)),
        photoUri = getString(getColumnIndexOrThrow(Columns.PHOTO_URI)),
        latitude = getDoubleOrNull(Columns.LATITUDE),
        longitude = getDoubleOrNull(Columns.LONGITUDE)
    )

    private fun Cursor.getDoubleOrNull(columnName: String): Double? {
        val index = getColumnIndexOrThrow(columnName)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun toContentValues(contact: Contact) = ContentValues().apply {
        put(Columns.NOM, contact.nom)
        put(Columns.PRENOM, contact.prenom)
        put(Columns.TELEPHONE, contact.telephone)
        if (contact.email == null) putNull(Columns.EMAIL) else put(Columns.EMAIL, contact.email)
        if (contact.photoUri == null) putNull(Columns.PHOTO_URI) else put(Columns.PHOTO_URI, contact.photoUri)
        if (contact.latitude == null) putNull(Columns.LATITUDE) else put(Columns.LATITUDE, contact.latitude)
        if (contact.longitude == null) putNull(Columns.LONGITUDE) else put(Columns.LONGITUDE, contact.longitude)
    }

    /** Protège les caractères spéciaux LIKE sans jamais concaténer une saisie dans le SQL. */
    private fun escapeLike(value: String): String = value
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
