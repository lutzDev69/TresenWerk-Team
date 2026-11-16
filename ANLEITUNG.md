# TresenWerk Team – Anleitung

> Team-Variante (App-Kennung `de.tresenwerk.team`). Die Offline-Variante liegt im Repository **TresenWerk**.
> Was unten über Funktionen steht, gilt für beide; die Serveranbindung kommt nach [docs/Konzept-Serveranbindung.md](docs/Konzept-Serveranbindung.md).

Rezept- und Bestands-App für professionelle Bartender. Alle Daten bleiben auf dem Gerät (kein Konto). Internet wird nur für die Barcode-Abfrage gebraucht.

## Funktionen (Version 0.5.2)

- **Rezepte**: Name, Zutaten mit Menge und Einheit (ml, cl, oz, Dash, Tropfen, Barlöffel, Stück, Auffüllen), Methode, Glas, Eis, Garnitur, Kategorie, Tags, Zubereitung, Notizen, Foto
- **Suche & Filter**: Volltextsuche (mehrere Wörter = alle müssen vorkommen), Filter nach Favoriten, Methode und Tag
- **Batch-Rechner**: Rezept auf beliebig viele Portionen hochrechnen, Umrechnung ml/cl/oz, Verdünnungswasser für Pre-Batches
- **Bar-Bestand**: Flaschen mit Größe, Alkoholgehalt, Anzahl und Füllstand der angebrochenen Flasche; „Leer – nächste öffnen“; Filter „Nachkaufen“ (keine Reserve und ≤ 20 %)
- **Etikett scannen**: Foto der Flasche → Texterkennung auf dem Gerät (Google ML Kit) schlägt Name, Größe und % vol vor
- **Barcode scannen**: Barcode fotografieren → Name, Marke, Größe, % vol und Sorte aus Open Food Facts (gesendet wird nur die Barcode-Nummer). Klappt auch ohne Netz: der Barcode wird gespeichert und die Flasche beim nächsten Scan wiedererkannt
- **Wiedererkennen**: Scannt man eine Flasche erneut (Barcode oder ähnliches Etikett), bietet die App „Diese aktualisieren“ an – praktisch zum Nachmessen des Füllstands. Einmal korrigierte Namen bleiben so erhalten
- **Sorte erkennen**: „Dry Gin“, „Bourbon“, „Liqueur“ … füllt die Kategorie automatisch
- **Füllstand am Foto messen**: drei Linien auf Boden, Voll-Höhe und Flüssigkeit schieben → Prozent und Rest-ml
- **Bestandsanzeige im Rezept**: neben jeder Zutat steht der Füllstand der passenden Flasche oder „nicht im Bestand“
- **Was kann ich mixen?**: Filter „Kann ich mixen“ in der Rezeptliste; bei den anderen Rezepten steht, was fehlt. Frische Zutaten und Grundvorrat (Saft, Sirup, Soda, Minze, Ei …) gelten als vorhanden
- **Einkaufsliste**: „Einkaufsliste“ oben im Bar-Bestand teilt alle Flaschen mit ≤ 20 % als Liste (nach Sorte, mit letztem Preis)
- **Kosten pro Drink**: Einkaufspreis bei der Flasche eintragen → im Rezept Wareneinsatz pro Drink und Verkaufspreis-Richtwert bei 15–30 % Wareneinsatz
- **Inventur für den Steuerberater**: „Daten → Inventur als CSV exportieren“ – alle Flaschen mit vollen/angebrochenen Flaschen, Bestand, Einkaufspreis netto, Warenwert und Summe; öffnet direkt in deutschem Excel. Angebrochene Flaschen anteilig bewertet, Flaschen ohne Preis mit Hinweis
- **Sicherung**: Export/Import als ZIP-Datei mit allen Fotos (ältere JSON-Sicherungen lassen sich weiter einlesen), Rezept als Text teilen

## Projektaufbau (was ist was?)

| Datei | Zweck |
|---|---|
| `gradle/libs.versions.toml` | Zentrale Liste aller Bibliotheken und Versionen |
| `app/build.gradle.kts` | Build-Einstellungen der App |
| `app/src/main/AndroidManifest.xml` | „Ausweis“ der App: Name, Symbol, Kamera-Zugriff |
| `.../MainActivity.kt` | Navigation zwischen den Bildschirmen, untere Leiste |
| `.../MainViewModel.kt` | Bindeglied Bildschirme ↔ Datenbank, Export/Import |
| `.../data/Entities.kt` | Tabellen: Rezept, Zutat, Flasche |
| `.../data/Database.kt` | Datenbank-Abfragen (Room) |
| `.../data/Backup.kt` | Rezepte/Flaschen als JSON schreiben und lesen |
| `.../data/BackupArchive.kt` | ZIP-Sicherung: backup.json plus Ordner photos/ |
| `.../data/SampleRecipes.kt` | Beispielrezepte |
| `.../domain/Measure.kt` | Einheiten, Umrechnung, Zahlenformat |
| `.../domain/BatchCalculator.kt` | Hochrechnen auf Portionen, Verdünnung |
| `.../domain/Search.kt` | Rezeptsuche, Zuordnung Zutat → Flasche, „Kann ich mixen?“ |
| `.../domain/Costing.kt` | Kosten pro Drink aus Flaschenpreisen |
| `.../domain/ShoppingList.kt` | Einkaufsliste als Text |
| `.../domain/InventoryCsv.kt` | Inventurliste als CSV für den Steuerberater |
| `.../scan/LabelScanner.kt` | Texterkennung und Barcode-Lesen auf dem Foto (ML Kit, offline) |
| `.../scan/LabelParser.kt` | Erkennt im Text Name, Größe, % vol, Sorte; erkennt vorhandene Flaschen wieder |
| `.../scan/ProductLookup.kt` | Barcode-Abfrage bei Open Food Facts |
| `.../scan/FillLevel.kt` | Berechnung des Füllstands aus den drei Linien |
| `.../util/Photos.kt` | Fotos verkleinern, drehen, speichern, aufräumen |
| `.../ui/*Screen.kt` | Die einzelnen Bildschirme |
| `.../ui/FillLevelDialog.kt` | Füllstand-Messung am Foto |
| `app/src/main/res/drawable/ic_launcher_*.xml` | App-Symbol (TW-Monogramm): Hintergrund, Vordergrund, einfarbige Variante |
| `docs/logo/` | Logo als SVG und 512-px-Bild für den Play Store |
| `app/src/test/...` | Automatische Tests (Batch, Suche, Etikett, Barcode, Füllstand, Sicherung) |

## App bauen und installieren

Handy vorbereiten (Entwickleroptionen, USB-Debugging) wie beim RadLogger.

**Android Studio:** *Open* → Ordner `TresenWerk` → Gradle-Sync abwarten → Fairphone wählen → ▶.

**Terminal:**

```bash
cd /media/lutz/data/Android/TresenWerk
./gradlew installDebug
```

Tests ausführen:

```bash
./gradlew testDebugUnitTest
```

## Release-Version für Tester

Es gibt zwei Varianten der App:

| Variante | Name auf dem Handy | Wofür |
|---|---|---|
| Debug (`./gradlew installDebug`) | TresenWerk Team Dev | Entwickeln und Ausprobieren |
| Release (`./release.sh`) | TresenWerk Team | Weitergeben an Tester |

Beide können nebeneinander installiert sein und haben getrennte Daten.

**Release bauen:**

```bash
./release.sh
```

Ergebnis: `dist/TresenWerk-Team-<Version>.apk` – diese Datei weitergeben (Messenger, Mail, Cloud-Link).
Vor jeder neuen Version in `app/build.gradle.kts` `versionCode` um 1 erhöhen und `versionName` anpassen,
sonst lässt sich das Update nicht über die alte Version installieren.

**Kurzanleitung für Tester:** `docs/Kurzanleitung.html` (Versionsnummer an drei Stellen anpassen: Kopf, Fußzeile in `@page`, APK-Dateiname) als PDF erzeugen und mit der APK verschicken:

```bash
google-chrome --headless=new --no-pdf-header-footer --print-to-pdf=dist/TresenWerk-Kurzanleitung.pdf "file://$PWD/docs/Kurzanleitung.html"
```

**Schlüssel – unbedingt sichern!**
- Schlüssel: `/media/lutz/data/Android/keys/tresenwerk-release.jks`
- Passwort: steht in `keystore.properties` (nur lokal, nicht in Git)
- Ohne Schlüssel und Passwort können Tester keine Updates mehr installieren, ohne die App (und ihre Daten) zu löschen.
  Beides an einem zweiten Ort aufbewahren (USB-Stick, Passwort-Manager).

**Einrichtung auf einem neuen Rechner:** Schlüssel nach `keys/` kopieren, `keystore.properties.example`
als `keystore.properties` kopieren und Passwort bei `storePassword` und `keyPassword` eintragen (beide gleich).

## Sicherheit und Datenschutz

- Ins Internet geht nur die Barcode-Nummer (an Open Food Facts, verschlüsselt). Googles Nutzungsstatistik von ML Kit
  ist im `AndroidManifest.xml` entfernt (`tools:node="remove"`) – beim Scannen erscheint im Protokoll dafür
  „Transport backend 'cct' is not registered“, das ist gewollt.
- Inventur-CSV: Felder, die mit `=`, `+`, `-` oder `@` beginnen, bekommen ein `'` vorangestellt (Schutz vor Excel-Formeln).
- Sicherung einlesen: Größengrenzen (20 MB Daten, 20 MB pro Foto, 1 GB gesamt, 10.000 Einträge), Werte werden auf
  sinnvolle Bereiche begrenzt, nur einfache Bild-Dateinamen werden übernommen.
- Erkannter Etikett-Text wird nur in der Entwicklerversion ins Systemprotokoll geschrieben.
- Exportierte Sicherungen/Inventuren sind unverschlüsselt (inkl. Einkaufspreisen) – sicher aufbewahren.

## Tipps zur Bedienung

- **Schreibschrift-Logos** (z. B. Friedrichs) liest die Texterkennung oft falsch. Solche unsicheren Zeilen schlägt die App nicht mehr als Name vor – dann den Barcode scannen oder den Namen einmal eintippen.
- **Etikett scannen**: Flasche gerade und formatfüllend fotografieren, gutes Licht, keine Spiegelungen. Erkannte Textzeilen antippen, um Name oder Marke zu ergänzen.
- **Füllstand**: Für die Messung die Flasche frontal auf Augenhöhe fotografieren. Grün = Innenboden, Blau = Höhe einer vollen Flasche (meist die Schulter), Gelb = Flüssigkeit. Die Rechnung ist linear; bei bauchigen Flaschen ist sie nur ein Richtwert.
- **Inventur für den Steuerberater**: „Daten → Inventur als CSV exportieren“ – alle Flaschen mit vollen/angebrochenen Flaschen, Bestand, Einkaufspreis netto, Warenwert und Summe; öffnet direkt in deutschem Excel. Angebrochene Flaschen anteilig bewertet, Flaschen ohne Preis mit Hinweis
- **Sicherung**: regelmäßig unter „Daten“ erstellen – bei Deinstallation der App sind sonst alle Daten weg.

## Grenzen der Version 0.5.2

- Der Füllstand wird nicht automatisch erkannt, sondern mit den Linien gemessen.
- Kleine Marken fehlen oft in Open Food Facts; dann Name einmal selbst eintragen.
- Die Zuordnung Zutat → Flasche läuft über ganze Wörter und die Sorte (z. B. „Gin“ findet „Tanqueray London Dry Gin“, „Bourbon“ eine Flasche der Sorte Whisk(e)y). Am sichersten: Zutaten im Rezept so nennen wie die Flasche oder ihre Sorte.
- Kosten für Dash, Tropfen und Barlöffel sind Näherungen (0,8 ml / 0,05 ml / 5 ml). Frische Zutaten haben keinen Preis.
