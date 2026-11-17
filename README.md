# Contacts V4 — TP Android

Application de gestion de contacts en **Kotlin / XML**, avec persistance **SQLite native**,
préférences utilisateur, fragments dynamiques et carte **OpenStreetMap**.

> Choix du sujet : la carte est assurée par **osmdroid** (OpenStreetMap) plutôt que Google Maps.
> Il n’y a donc **aucune clé API à obtenir, aucun fichier de secrets à versionner**, et le dépôt
> peut être publié tel quel sur GitHub.

## 1. Prérequis

- Android Studio (AGP 8.7 / Kotlin 2.0 fonctionnent avec les versions récentes).
- JDK 17 (fourni par Android Studio).
- SDK Android installé avec **API 35** (compileSdk / targetSdk) et **minSdk 24**.
- Connexion internet au premier build (Gradle + dépendances + tuiles OSM).

## 2. Ouvrir et lancer

1. `File > Open…` puis sélectionner ce dossier (celui qui contient `settings.gradle.kts`).
2. Laisser Android Studio synchroniser Gradle.
3. Run sur un émulateur ou un appareil (API 24+).

En ligne de commande :

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

Le wrapper Gradle 8.9 est fourni (`gradlew`, `gradlew.bat`, `gradle/wrapper/`).
La compilation `assembleDebug` a été vérifiée avec succès (SDK Android API 35, JDK 17) ;
l’APK se trouve dans `app/build/outputs/apk/debug/app-debug.apk`.

## 3. Ce qui est implémenté

### Persistance

- `contacts.db` créé par `ContactDbHelper` (singleton, `applicationContext`).
- 5 contacts de démonstration insérés une seule fois (`onCreate` uniquement), dont 2 sans GPS.
- Deux noms avec apostrophe (`O'Neil`, `L'Ariana`) pour valider les requêtes paramétrées.
- `ContactDao` : `insert`, `update`, `delete`, `getAll`, `getById`, `search`.
- Tri `nom COLLATE NOCASE, prenom COLLATE NOCASE`.
- Photos copiées dans `filesDir/photos` (URI de galerie non persistées).
- Toutes les requêtes variables utilisent `?` / `selectionArgs` ; chaque `Cursor` est fermé avec `use {}`.

### Interface

- `MainActivity` : `FragmentContainerView`, `replace()`, `addToBackStack()`, flèche Up, menu Paramètres.
- `ListeContactsFragment` : RecyclerView, filtrage immédiat insensible à la casse, FAB « + »,
  bouton Appeler par ligne selon la préférence.
- `DetailContactFragment` : création / consultation / modification / suppression confirmée,
  validation, Annuler, Appeler (`ACTION_DIAL`), E-mail, Galerie, Caméra, Carte.
- `CarteContactFragment` : marqueur OSM ; appui long pour renvoyer des coordonnées à la fiche.
- `ParametresFragment` : `ListPreference` taille, `ListPreference` couleur, `SwitchPreferenceCompat` appel.

### Préférences

Clés dans les `SharedPreferences` par défaut :

| Clé | Défaut | Effet |
|---|---|---|
| `taille_police` | `16` | 14 / 16 / 20 sp sur nom, prénom, téléphone (liste et détail) |
| `couleur_preferee` | `#000000` | Couleur du nom et du prénom (liste et détail) |
| `appel_depuis_liste` | `false` | Affiche / masque le bouton Appeler de chaque ligne |

`PreferencesManager` convertit les valeurs brutes en `UiPreferences` (Float, Int, Boolean).
`ListeContactsFragment` relit les préférences dans `onResume()` **et** via un
`OnSharedPreferenceChangeListener` : l’application est immédiate, sans redémarrage.

> Les trois tailles sont appliquées aux champs nom / prénom / téléphone de la liste **et** du
> détail ; la couleur s’applique au nom et au prénom dans les deux écrans.

## 4. Arborescence

```
app/src/main/java/com/example/contacts/
├── MainActivity.kt
├── data/
│   ├── Contact.kt
│   ├── ContactContract.kt
│   ├── ContactDbHelper.kt
│   ├── ContactDao.kt
│   └── PreferencesManager.kt
├── ui/
│   ├── ContactViewModel.kt
│   ├── ContactListAdapter.kt
│   ├── ListeContactsFragment.kt
│   ├── DetailContactFragment.kt
│   ├── CarteContactFragment.kt
│   └── ParametresFragment.kt
└── util/
    ├── PhotoStorage.kt
    └── ContactActions.kt
```

Ressources : `res/xml/preferences.xml`, `res/xml/file_paths.xml`,
`res/values/arrays.xml`, layouts et menu.

## 5. Vérification rapide

1. Premier lancement : 5 contacts de démo, triés. Deuxième lancement : toujours 5.
2. `+` → créer « O'Neil Test » avec `+216 12 345 678` → enregistrer → apparaît trié.
3. Filtrer avec `'` ou `L'Ariana` → aucun crash SQL.
4. Laisser nom/prénom/téléphone vide → messages d’erreur, rien en base.
5. Paramètres → Grande + Rouge + appel activé → retour : liste mise à jour immédiatement.
6. Bouton Appeler d’une ligne → composeur (`ACTION_DIAL`), pas la fiche.
7. Détail → Supprimer → confirmation → contact retiré.
8. Glisser l’app hors des récentes puis relancer : contacts, photos et préférences conservés.
9. Détail → Voir sur la carte → marqueur ; appui long → coordonnées renvoyées dans le formulaire.
10. Database Inspector : table `contacts`, 8 colonnes, tri et valeurs NULL sur GPS.

## 6. Captures à produire (à faire dans Android Studio)

- Liste au premier lancement, création, modification, confirmation de suppression.
- Liste après relance, Database Inspector (table `contacts`), écran Paramètres.
- Liste avec deux tailles et deux couleurs, boutons Appeler activés, composeur ouvert.
- Carte avec marqueur.

Voir `COMPTE_RENDU.md` pour l’analyse technique détaillée et les réponses aux questions.

## 7. Publication GitHub

Aucun secret n’est nécessaire : ne pas ajouter de `google-services.json`, de clé Maps ni de
`local.properties` contenant des identifiants. Le `.gitignore` exclut déjà `local.properties`.

```powershell
git init
git add .
git commit -m "TP Android V4 - Contacts SQLite, preferences et OSM"
git remote add origin <URL_DU_DEPOT>
git push -u origin main
```
