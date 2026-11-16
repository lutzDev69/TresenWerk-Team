# TresenWerk – Anleitung

Rezept- und Bestands-App für professionelle Bartender. Alle Daten bleiben auf dem Gerät (kein Konto). Internet wird nur für die Barcode-Abfrage gebraucht.

## Funktionen (Version 0.3)

- **Rezepte**: Name, Zutaten mit Menge und Einheit (ml, cl, oz, Dash, Tropfen, Barlöffel, Stück, Auffüllen), Methode, Glas, Eis, Garnitur, Kategorie, Tags, Zubereitung, Notizen, Foto
- **Suche & Filter**: Volltextsuche (mehrere Wörter = alle müssen vorkommen), Filter nach Favoriten, Methode und Tag
- **Batch-Rechner**: Rezept auf beliebig viele Portionen hochrechnen, Umrechnung ml/cl/oz, Verdünnungswasser für Pre-Batches
- **Bar-Bestand**: Flaschen mit Größe, Alkoholgehalt und Füllstand; Filter „Nachkaufen“ (≤ 20 %)
- **Etikett scannen**: Foto der Flasche → Texterkennung auf dem Gerät (Google ML Kit) schlägt Name, Größe und % vol vor
- **Barcode scannen**: Barcode fotografieren → Name, Marke, Größe, % vol und Sorte aus Open Food Facts (gesendet wird nur die Barcode-Nummer). Klappt auch ohne Netz: der Barcode wird gespeichert und die Flasche beim nächsten Scan wiedererkannt
- **Wiedererkennen**: Scannt man eine Flasche erneut (Barcode oder ähnliches Etikett), bietet die App „Diese aktualisieren“ an – praktisch zum Nachmessen des Füllstands. Einmal korrigierte Namen bleiben so erhalten
- **Sorte erkennen**: „Dry Gin“, „Bourbon“, „Liqueur“ … füllt die Kategorie automatisch
- **Füllstand am Foto messen**: drei Linien auf Boden, Voll-Höhe und Flüssigkeit schieben → Prozent und Rest-ml
- **Bestandsanzeige im Rezept**: neben jeder Zutat steht der Füllstand der passenden Flasche oder „nicht im Bestand“
- **Was kann ich mixen?**: Filter „Kann ich mixen“ in der Rezeptliste; bei den anderen Rezepten steht, was fehlt. Frische Zutaten und Grundvorrat (Saft, Sirup, Soda, Minze, Ei …) gelten als vorhanden
- **Einkaufsliste**: „Einkaufsliste“ oben im Bar-Bestand teilt alle Flaschen mit ≤ 20 % als Liste (nach Sorte, mit letztem Preis)
- **Kosten pro Drink**: Einkaufspreis bei der Flasche eintragen → im Rezept Wareneinsatz pro Drink und Verkaufspreis-Richtwert bei 15–30 % Wareneinsatz
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
| `.../scan/LabelScanner.kt` | Texterkennung und Barcode-Lesen auf dem Foto (ML Kit, offline) |
| `.../scan/LabelParser.kt` | Erkennt im Text Name, Größe, % vol, Sorte; erkennt vorhandene Flaschen wieder |
| `.../scan/ProductLookup.kt` | Barcode-Abfrage bei Open Food Facts |
| `.../scan/FillLevel.kt` | Berechnung des Füllstands aus den drei Linien |
| `.../util/Photos.kt` | Fotos verkleinern, drehen, speichern, aufräumen |
| `.../ui/*Screen.kt` | Die einzelnen Bildschirme |
| `.../ui/FillLevelDialog.kt` | Füllstand-Messung am Foto |
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

## Tipps zur Bedienung

- **Schreibschrift-Logos** (z. B. Friedrichs) liest die Texterkennung oft falsch. Solche unsicheren Zeilen schlägt die App nicht mehr als Name vor – dann den Barcode scannen oder den Namen einmal eintippen.
- **Etikett scannen**: Flasche gerade und formatfüllend fotografieren, gutes Licht, keine Spiegelungen. Erkannte Textzeilen antippen, um Name oder Marke zu ergänzen.
- **Füllstand**: Für die Messung die Flasche frontal auf Augenhöhe fotografieren. Grün = Innenboden, Blau = Höhe einer vollen Flasche (meist die Schulter), Gelb = Flüssigkeit. Die Rechnung ist linear; bei bauchigen Flaschen ist sie nur ein Richtwert.
- **Sicherung**: regelmäßig unter „Daten“ erstellen – bei Deinstallation der App sind sonst alle Daten weg.

## Grenzen der Version 0.3

- Der Füllstand wird nicht automatisch erkannt, sondern mit den Linien gemessen.
- Kleine Marken fehlen oft in Open Food Facts; dann Name einmal selbst eintragen.
- Die Zuordnung Zutat → Flasche läuft über ganze Wörter und die Sorte (z. B. „Gin“ findet „Tanqueray London Dry Gin“, „Bourbon“ eine Flasche der Sorte Whisk(e)y). Am sichersten: Zutaten im Rezept so nennen wie die Flasche oder ihre Sorte.
- Kosten für Dash, Tropfen und Barlöffel sind Näherungen (0,8 ml / 0,05 ml / 5 ml). Frische Zutaten haben keinen Preis.
