package com.example.contacts.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Cycle de vie de contacts.db. Instance unique construite avec applicationContext :
 * éviter de multiplier les connexions à la même base lors des rotations.
 */
class ContactDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ContactContract.CREATE_TABLE)
        db.beginTransaction()
        try {
            insertDemoContacts(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // TP V4 : schéma volontairement simple. Une évolution future (ex. colonne
        // favori en version 2) se fera avec un ALTER TABLE sans détruire les données.
        db.execSQL(ContactContract.DROP_TABLE)
        onCreate(db)
    }

    private fun insertDemoContacts(db: SQLiteDatabase) {
        listOf(
            DemoContact("Ben Ahmed", "Mohamed", "+216 20 123 456", "mohamed.benahmed@example.com", 36.8065, 10.1815),
            DemoContact("O'Neil", "Sarah", "+216 91 234 567", "sarah.oneil@example.com", null, null),
            DemoContact("Trabelsi", "Karim", "02 345 678", "karim.trabelsi@example.com", 34.7406, 10.7603),
            DemoContact("L'Ariana", "Inès", "03 456 789", "ines.lariana@example.com", null, null),
            DemoContact("Bouchiba", "Nour", "+216 55 111 222", null, 35.8256, 10.6084)
        ).forEach { demo ->
            val values = ContentValues().apply {
                put(ContactContract.Columns.NOM, demo.nom)
                put(ContactContract.Columns.PRENOM, demo.prenom)
                put(ContactContract.Columns.TELEPHONE, demo.telephone)
                put(ContactContract.Columns.EMAIL, demo.email)
                putNull(ContactContract.Columns.PHOTO_URI)
                if (demo.latitude == null) putNull(ContactContract.Columns.LATITUDE)
                else put(ContactContract.Columns.LATITUDE, demo.latitude)
                if (demo.longitude == null) putNull(ContactContract.Columns.LONGITUDE)
                else put(ContactContract.Columns.LONGITUDE, demo.longitude)
            }
            db.insert(ContactContract.TABLE_NAME, null, values)
        }
    }

    private data class DemoContact(
        val nom: String,
        val prenom: String,
        val telephone: String,
        val email: String?,
        val latitude: Double?,
        val longitude: Double?
    )

    companion object {
        const val DATABASE_NAME = "contacts.db"
        const val DATABASE_VERSION = 1

        @Volatile
        private var instance: ContactDbHelper? = null

        fun getInstance(context: Context): ContactDbHelper =
            instance ?: synchronized(this) {
                instance ?: ContactDbHelper(context.applicationContext).also { instance = it }
            }
    }
}
