package com.example.contacts.data

/**
 * Seul endroit où sont déclarés le nom de la table et ceux des colonnes.
 * Le DAO, le helper et l'UI réutilisent ces constantes.
 */
object ContactContract {
    const val TABLE_NAME = "contacts"

    object Columns {
        const val ID = "_id"
        const val NOM = "nom"
        const val PRENOM = "prenom"
        const val TELEPHONE = "telephone"
        const val EMAIL = "email"
        const val PHOTO_URI = "photo_uri"
        const val LATITUDE = "latitude"
        const val LONGITUDE = "longitude"
    }

    val ALL_COLUMNS = arrayOf(
        Columns.ID,
        Columns.NOM,
        Columns.PRENOM,
        Columns.TELEPHONE,
        Columns.EMAIL,
        Columns.PHOTO_URI,
        Columns.LATITUDE,
        Columns.LONGITUDE
    )

    val CREATE_TABLE = """
        CREATE TABLE $TABLE_NAME (
            ${Columns.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
            ${Columns.NOM} TEXT NOT NULL,
            ${Columns.PRENOM} TEXT NOT NULL,
            ${Columns.TELEPHONE} TEXT NOT NULL,
            ${Columns.EMAIL} TEXT,
            ${Columns.PHOTO_URI} TEXT,
            ${Columns.LATITUDE} REAL,
            ${Columns.LONGITUDE} REAL
        )
    """.trimIndent()

    val DROP_TABLE = "DROP TABLE IF EXISTS $TABLE_NAME"

    /** Tri insensible à la casse, exigé pour l'affichage de la liste. */
    val ORDER_BY = "${Columns.NOM} COLLATE NOCASE ASC, ${Columns.PRENOM} COLLATE NOCASE ASC"
}
