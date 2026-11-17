package com.example.contacts.ui

import android.app.Application
import android.content.SharedPreferences
import android.database.sqlite.SQLiteException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.contacts.R
import com.example.contacts.data.Contact
import com.example.contacts.data.ContactDao
import com.example.contacts.data.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** ViewModel partagé par toutes les fragments via activityViewModels(). */
class ContactViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = ContactDao(application)
    private val preferencesManager = PreferencesManager(application)

    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts: StateFlow<List<Contact>> = _contacts.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /**
     * Choix V4 : filtrage en mémoire sur la liste déjà triée par le DAO.
     * Justification : la frappe ne déclenche aucune requête disque, le tri COLLATE
     * NOCASE est appliqué une seule fois par la base, et le résultat reste instantané.
     */
    val visibleContacts: StateFlow<List<Contact>> = combine(_contacts, _query) { list, text ->
        if (text.isBlank()) {
            list
        } else {
            val needle = text.trim()
            list.filter { contact ->
                contact.nom.contains(needle, ignoreCase = true) ||
                    contact.prenom.contains(needle, ignoreCase = true) ||
                    contact.telephone.contains(needle, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _preferences = MutableStateFlow(preferencesManager.read())
    val preferences: StateFlow<PreferencesManager.UiPreferences> = _preferences.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _contacts.value = withContext(Dispatchers.IO) { dao.getAll() }
        }
    }

    /** Chargement ponctuel de la fiche : appelé depuis lifecycleScope par le fragment. */
    suspend fun loadContact(id: Long): Contact? = withContext(Dispatchers.IO) {
        if (id == 0L) null else dao.getById(id)
    }

    fun setQuery(value: String) {
        _query.value = value
    }

    fun refreshPreferences() {
        _preferences.value = preferencesManager.read()
    }

    fun registerPreferencesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferencesManager.register(listener)

    fun unregisterPreferencesListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferencesManager.unregister(listener)

    fun save(contact: Contact) {
        viewModelScope.launch {
            val outcome = try {
                withContext(Dispatchers.IO) {
                    if (contact.id == 0L) {
                        val newId = dao.insert(contact)
                        if (newId > 0L) SaveOutcome.Ok(newId, created = true)
                        else SaveOutcome.Error(getApplication<Application>().getString(R.string.error_insert))
                    } else {
                        val rows = dao.update(contact)
                        if (rows > 0) SaveOutcome.Ok(contact.id, created = false)
                        else SaveOutcome.Error(getApplication<Application>().getString(R.string.error_update))
                    }
                }
            } catch (_: SQLiteException) {
                SaveOutcome.Error(getApplication<Application>().getString(R.string.error_insert))
            }

            when (outcome) {
                is SaveOutcome.Ok -> {
                    refresh()
                    _events.emit(UiEvent.Message(getApplication<Application>().getString(R.string.saved)))
                    _events.emit(UiEvent.Saved(outcome.id, outcome.created))
                }
                is SaveOutcome.Error -> _events.emit(UiEvent.Message(outcome.message))
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            val rows = try {
                withContext(Dispatchers.IO) { dao.delete(id) }
            } catch (_: SQLiteException) {
                0
            }
            if (rows > 0) {
                refresh()
                _events.emit(UiEvent.Message(getApplication<Application>().getString(R.string.deleted)))
                _events.emit(UiEvent.Deleted)
            } else {
                _events.emit(UiEvent.Message(getApplication<Application>().getString(R.string.error_not_found)))
            }
        }
    }

    private sealed interface SaveOutcome {
        data class Ok(val id: Long, val created: Boolean) : SaveOutcome
        data class Error(val message: String) : SaveOutcome
    }

    sealed interface UiEvent {
        data class Message(val text: String) : UiEvent
        data class Saved(val id: Long, val created: Boolean) : UiEvent
        data object Deleted : UiEvent
    }
}
