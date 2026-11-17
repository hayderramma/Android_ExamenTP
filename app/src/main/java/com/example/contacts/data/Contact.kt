package com.example.contacts.data

/**
 * Modèle métier. [id] vaut 0 tant que le contact n'a pas été inséré :
 * SQLite attribue alors _id via AUTOINCREMENT.
 */
data class Contact(
    val id: Long = 0L,
    val nom: String,
    val prenom: String,
    val telephone: String,
    val email: String? = null,
    val photoUri: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    /** Une position n'est exploitable que si les deux coordonnées sont présentes. */
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null

    val displayName: String
        get() = "$prenom $nom".trim()
}
