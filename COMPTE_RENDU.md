# Compte rendu — TP Android V4 : Gestion des contacts

Projet : `ContactsV4` — Kotlin, AndroidX, SQLite natif, OpenStreetMap (osmdroid).
Ce document peut être exporté en PDF (par exemple via `Ctrl+P > Enregistrer au format PDF`
depuis un éditeur Markdown ou Typora).

---

## 1. Schéma de la base et choix des types

```sql
CREATE TABLE contacts (
    _id         INTEGER PRIMARY KEY AUTOINCREMENT,
    nom         TEXT NOT NULL,
    prenom      TEXT NOT NULL,
    telephone   TEXT NOT NULL,
    email       TEXT,
    photo_uri   TEXT,
    latitude    REAL,
    longitude   REAL
);
```

| Colonne | Type SQLite | Justification |
|---|---|---|
| `_id` | `INTEGER PRIMARY KEY AUTOINCREMENT` | Identifiant stable, jamais réutilisé. C’est lui qui circule dans les Bundle, le ViewModel et l’adapter — pas la position dans la RecyclerView. |
| `nom`, `prenom`, `telephone` | `TEXT NOT NULL` | Chaine obligatoire. Le **téléphone est en TEXT** : il peut commencer par `0` ou `+216` et contenir des espaces ; un `INTEGER` supprimerait les zéros initiaux et refuserait `+`. |
| `email` | `TEXT` (nullable) | Facultatif. `NULL` stocké quand le champ est vide. |
| `photo_uri` | `TEXT` (nullable) | Chemin **absolu** d’une copie locale dans `filesDir/photos`, pas une URI de galerie temporaire. |
| `latitude`, `longitude` | `REAL` (nullable) | `Double?`. Une position absente est `NULL` dans **les deux** colonnes ; on n’enregistre jamais une seule coordonnée. |

Le tri est `nom COLLATE NOCASE ASC, prenom COLLATE NOCASE ASC` (déclaré dans `ContactContract.ORDER_BY`).

## 2. Rôle du helper et du DAO

### `ContactDbHelper`

- Hérite de `SQLiteOpenHelper`, base `contacts.db`, `DATABASE_VERSION = 1`.
- `onCreate(db)` : exécute le DDL puis insère **5 contacts de démonstration** dans une transaction
  (`beginTransaction` / `setTransactionSuccessful` / `endTransaction`). Deux n’ont pas de GPS.
- `onUpgrade(db, old, new)` : pour l’instant `DROP TABLE` + `onCreate`. Une vraie migration
  (bonus `favori INTEGER DEFAULT 0`) doit utiliser `ALTER TABLE` pour conserver les données.
- **Singleton** avec `@Volatile` + `synchronized`, construit avec `context.applicationContext` :
  une seule connexion, aucune fuite d’`Activity` lors des rotations.

### `ContactDao`

Seule classe contenant du SQL (le helper ne contient que le DDL). API :
`insert(): Long`, `update(): Int`, `delete(): Int`, `getAll(): List<Contact>`,
`getById(): Contact?`, `search(texte): List<Contact>`.

- Valeurs variables → `selectionArgs` (`?`). Aucune concaténation de saisie utilisateur.
- `search()` utilise `LIKE ? ESCAPE '\'` avec échappement de `\`, `%` et `_`.
- Chaque `Cursor` est fermé par `use { }` ; les colonnes sont lues avec `getColumnIndexOrThrow()`.
- `getDoubleOrNull()` teste `isNull()` avant `getDouble()` : distinction entre `0.0` et `NULL`.
- `insert()` renvoie le `_id` généré ou `-1` ; `update()` / `delete()` renvoient le nombre de lignes.
  Le ViewModel transforme ces valeurs en messages d’erreur ou en événement `Saved` / `Deleted`.

## 3. Stratégie de threads

- Le DAO est synchrone ; **aucun appel n’est fait sur le thread principal**.
- `ContactViewModel` encapsule chaque accès dans `viewModelScope.launch { withContext(Dispatchers.IO) { … } }`.
- `refresh()`, `save()`, `delete()` mettent à jour des `StateFlow` / émettent des événements.
- `loadContact(id)` est une `suspend fun` qui fait elle-même `withContext(Dispatchers.IO)` et est
  appelée depuis le `lifecycleScope` du fragment de détail.
- Les fragments collectent les flows avec `repeatOnLifecycle(STARTED)` : pas de travail en arrière-plan,
  pas de mise à jour de vues après `onDestroyView`.

## 4. Choix du filtrage : **en mémoire**

Le filtrage est fait dans `ContactViewModel.visibleContacts` (`combine(contacts, query)`),
avec `contains(..., ignoreCase = true)` sur nom, prénom et téléphone.

**Justification** : la liste est déjà chargée et triée par SQLite ; une frappe ne déclenche donc
aucune requête disque, ce qui donne un retour instantané et évite de remplacer la liste à chaque
caractère. Le prix à payer est de charger tous les contacts (quelques centaines ici), ce qui est
trivial. La méthode SQL `ContactDao.search()` est néanmoins implémentée et testable : elle
conserve l’option « filtrage SQL » si le volume de données devenait important. Dans les deux cas,
la recherche est insensible à la casse.

## 5. Gestion des photos

- Galerie : `ActivityResultContracts.GetContent()` puis copie du flux `ContentResolver` vers
  `filesDir/photos/galerie_<timestamp>_<uuid>.jpg`.
- Caméra : `ActivityResultContracts.TakePicture()` avec une `Uri` `FileProvider` pointant déjà
  vers `filesDir/photos/camera_….jpg` ; annulation (`success == false`) → fichier supprimé, aucun crash.
- La seule valeur persistée en base est le **chemin absolu** de cette copie ; `PhotoStorage.loadBitmap()`
  décode avec `inSampleSize` pour limiter la mémoire.
- L’ancienne photo interne est supprimée lorsqu’elle est remplacée.
- Résultat : la photo survit à la fermeture complète de l’app et au redémarrage de l’appareil.

## 6. Préférences

- Stockage : `SharedPreferences` par défaut (et **non** SQLite) car ce sont 3 réglages clé/valeur
  simples, sans relation, sans tri ni requête. Les contacts, eux, sont relationnels et CRUD : SQLite.
- Écran : `ParametresFragment : PreferenceFragmentCompat`, `res/xml/preferences.xml`,
  tableaux dans `res/values/arrays.xml`, `useSimpleSummaryProvider` pour les deux listes,
  `summaryOn` / `summaryOff` pour l’interrupteur.
- Lecture centralisée : `PreferencesManager.read()` → `UiPreferences(textSizeSp, textColor, showCallButton)`.
  `Color.parseColor()` est protégé contre une valeur corrompue (repli noir).
- Application immédiate : `ListeContactsFragment.onResume()` + `OnSharedPreferenceChangeListener`
  enregistré dans `onResume()` / libéré dans `onPause()`. L’adapter reçoit des valeurs déjà converties
  via `applyPreferences()` et rafraîchit les lignes. Aucun redémarrage nécessaire.
- Couleurs : valeurs sombres (`#1565C0`, `#2E7D32`, `#C62828`, `#6A1B9A`, noir) sur fond blanc.
  Le thème est volontairement clair et figé (`Theme.MaterialComponents.Light.NoActionBar`) :
  le contraste reste correct, y compris pour le texte secundaire.

## 7. Robustesse et rotation

- `MainActivity` n’ajoute la liste que si `savedInstanceState == null` : pas de fragment doublé.
- Chaque `replace()` est accompagné de `addToBackStack()` ; la flèche Up et le bouton Retour
  dépilent correctement.
- Le formulaire sauvegarde ses 8 valeurs dans `onSaveInstanceState()` (dont le chemin photo et
  le fichier caméra en cours) : une saisie en cours n’est pas perdue à la rotation.
- Validation avant écriture : nom / prénom / téléphone obligatoires, e-mail formaté, coordonnées
  numériques et dans `[-90, 90] × [-180, 180]` ; sinon `null` en base et message explicite.
- `AlertDialog` de confirmation avant suppression ; `ACTION_DIAL` vérifie qu’une application
  existe (`resolveActivity`) et attrape `ActivityNotFoundException`.
- Carte : si les coordonnées sont absentes ou invalides, message explicite « Aucune coordonnée GPS… »
  et affichage d’une position par défaut ; aucun marqueur invalide n’est créé.

## 8. Carte OpenStreetMap (adaptation du sujet)

`CarteContactFragment` utilise `org.osmdroid:osmdroid-android:6.1.20` :

- `TileSourceFactory.MAPNIK`, `MapView` + `Marker`.
- Configuration dans le cache interne (`osmdroidBasePath`, `osmdroidTileCache`) : aucune
  permission de stockage externe, aucun secret.
- `userAgentValue` conforme à la politique des serveurs de tuiles OSM.
- Cycle de vie de la `MapView` (`onResume`, `onPause`, `onDetach`) correctement relayé.
- Bonus : appui long → `setFragmentResult()` renvoie latitude/longitude au formulaire.

## 9. Tests réalisés / à réaliser

| Test | Résultat attendu | Outil |
|---|---|---|
| 1er vs 2e lancement | 5 contacts de démo, pas de doublon | Liste + Database Inspector |
| CRUD complet + fermeture forcée | État identique après relance | App Inspection |
| Nom avec apostrophe + filtre `'` | Aucune erreur SQL | Jeu de données de démo |
| Champs obligatoires vides | `setError`, aucune écriture | Formulaire |
| Photo modifiée + redémarrage | Toujours affichée | Galerie / Caméra |
| GPS absent / non numérique / hors limites | `NULL` en base, message sur la carte | Formulaire + carte |
| 3 tailles × 5 couleurs | Rendu immédiat | Liste + Paramètres |
| Appel activé / désactivé | Bouton visible / `GONE`, composeur sans fiche | Liste |
| Préférences après relance | Valeurs conservées | Paramètres |
| Rotation liste / détail / paramètres | Pas de doublon, saisie conservée | Émulateur |
| Table `contacts` | 8 colonnes, tri NOCASE, NULL GPS | Database Inspector |

## 10. Réponses aux questions de compréhension

1. **`onCreate` / `onUpgrade`.** `onCreate` est appelé une seule fois, lors de la première ouverture
   d’un fichier de base inexistant. `onUpgrade` est appelé quand `DATABASE_VERSION` augmente.
   Si l’on modifie `onCreate` sans incrémenter la version, rien ne change sur un appareil qui a
   déjà `contacts.db` : il faut désinstaller l’app ou écrire une migration.

2. **`?` plutôt que concaténer.** Les valeurs sont liées par le moteur SQLite et ne peuvent jamais
   modifier la structure de la requête. Exemple : avec
   `"SELECT * FROM contacts WHERE nom = '" + saisie + "'"`, la saisie
   `x' OR '1'='1` donne `WHERE nom = 'x' OR '1'='1'` et renvoie toute la table. Avec `?`, cette
   chaine reste une simple valeur littérale.

3. **Retours de `insert` / `update` / `delete`.** `insert` renvoie le `_id` (ou `-1` en échec) :
   on l’utilise pour savoir si l’ajout a réussi et pour naviguer vers la fiche créée. `update` et
   `delete` renvoient un nombre de lignes : `0` signifie « introuvable / rien à faire », ce qui
   déclenche un message d’erreur compréhensible.

4. **Pourquoi pas le thread principal ?** SQLite fait des E/S disque : sur le thread UI, une requête
   lente provoque des gels de l’interface et peut déclencher un ANR. Ici, `withContext(Dispatchers.IO)`
   dans `viewModelScope` (ou `lifecycleScope` pour le chargement ponctuel) garantit que la UI
   reste fluide.

5. **Helper unique et `applicationContext`.** Une seule connexion évite les verrous et les
   incohérences ; construit avec le contexte application, le singleton ne retient pas d’`Activity`
   et ne fuite pas lors des rotations.

6. **SharedPreferences ou SQLite ?** SharedPreferences pour quelques clés/valeurs sans relations
   (préférences) : lecture simple, écriture automatique par AndroidX. SQLite pour un jeu de données
   structuré avec CRUD, tri, filtre et identifiants (contacts).

7. **Application sans redémarrage.** Le listener `OnSharedPreferenceChangeListener` + la relecture
   dans `onResume()` met à jour le `StateFlow` du ViewModel ; l’adapter reçoit de nouvelles valeurs
   converties et appelle `notifyItemRangeChanged()`.

8. **ACTION_DIAL vs ACTION_CALL.** `ACTION_DIAL` ouvre seulement le composeur prérempli : aucune
   permission, aucun appel effectué. `ACTION_CALL` passerait l’appel directement et exigerait
   `CALL_PHONE`, une permission dangereuse avec demande à l’exécution.

9. **Désinstallation / effacement des données.** Les deux suppriment `contacts.db` **et** les
   `SharedPreferences` (donc les 5 contacts de démo reviennent et les préférences reprennent leurs
   valeurs par défaut). Une simple mise à jour de l’application, elle, conserve les deux.

## 11. Difficultés rencontrées et limites connues

- Le passage de Google Maps à osmdroid a supprimé la gestion de la clé API mais impose de fixer un
  `userAgentValue` et un dossier de cache interne ; à défaut, les tuiles peuvent être refusées.
- Les URI `content://` de la galerie étant temporaires, la copie dans `filesDir` était indispensable :
  c’est le point le plus facile à oublier dans un TP de persistance.
- Le filtrage en mémoire simplifie la frappe mais serait à remplacer par `ContactDao.search()`
  (déjà prêt) si le nombre de contacts devenait très élevé.
- L’application est volontairement en thème clair fixe : les couleurs sombres proposées restent
  lisibles ; un thème sombre nécessiterait une autre palette de couleurs de texte.
