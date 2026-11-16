# TresenWerk – Konzept: Serveranbindung mit Benutzerverwaltung

Stand: 16.11.2026 · Status: **Entwurf zur Abstimmung** · bezieht sich auf App-Version 0.5.2

---

## 1. Ziel

Mehrere Geräte einer Bar (Handys, Tablet) sollen **denselben Rezept- und Flaschenbestand** sehen und bearbeiten.
Mitarbeiter melden sich mit eigenem Konto an; der Inhaber steuert, wer was darf.

**Nicht-Ziele** (bewusst ausgeklammert):
- Mehrere Bars / Mandanten – das System ist für **eine Bar**.
- Anbindung an das Kassensystem.
- Öffentliche Registrierung oder Zugriff für Gäste.

**Was bleibt:** Die App funktioniert weiterhin **ohne Internet**. Sicherung (ZIP), Inventur-CSV, Scan-Funktionen und
Datenschutz-Eigenschaften (keine Nutzungsstatistik, keine Drittanbieter außer der Barcode-Abfrage) bleiben erhalten.

---

## 2. Entscheidung: Webspace statt Kassen-Server

| Kriterium | Webspace | Server in der Bar (Kasse) |
|---|---|---|
| HTTPS | vorhanden | fehlt |
| Erreichbarkeit | überall | nur im WLAN der Bar (sonst Portfreigabe nötig) |
| Datensicherung | durch den Anbieter | selbst zu organisieren |
| Auswirkung auf die Kasse | keine | Zusatzsoftware auf dem Kassenrechner |
| Betrieb ohne Internet | App arbeitet offline weiter | im WLAN möglich |

**Entscheidung: Webspace.** Gründe:

- Kassensysteme unterliegen gesetzlichen Anforderungen (u. a. KassenSichV, GoBD). Fremdsoftware auf dem Kassenrechner
  kann Herstellergarantie, Support und Betriebsprüfungen gefährden.
- Ohne HTTPS würden Anmeldedaten im WLAN unverschlüsselt übertragen; Android blockiert unverschlüsselte Verbindungen
  standardmäßig.
- Zugriff von außen hieße, den Kassenrechner ins Internet zu stellen – ausgeschlossen.
- Der einzige Vorteil des Bar-Servers (Funktion ohne Internet) ist durch den Offline-Betrieb der App bereits abgedeckt.

**Voraussetzungen am Webspace** (laut Rückmeldung erfüllt): PHP ≥ 8.1, MySQL/MariaDB, HTTPS.
Noch zu klären: Subdomain, Ordner außerhalb des öffentlichen Webverzeichnisses, Upload-Weg (SFTP/SSH), Cron-Jobs.

---

## 3. Architektur

```
 ┌─────────────┐
 │  Handy 1    │──┐
 ├─────────────┤  │   HTTPS + Zugangsschlüssel     ┌──────────────── Webspace ───────────────┐
 │  Handy 2    │──┼──────────────────────────────► │  PHP-Schnittstelle (api/)                │
 ├─────────────┤  │   JSON                         │    ├─ Anmeldung, Benutzer, Rollen        │
 │  Tablet     │──┘                                │    ├─ Abgleich (sync)                    │
 └─────────────┘                                   │    └─ Fotos hoch-/herunterladen          │
   jede App mit eigener                            │  MySQL-Datenbank                         │
   lokaler Datenbank (Room)                        │  Foto-Ordner außerhalb des Webroots      │
                                                   └──────────────────────────────────────────┘
```

**Grundsatz „offline zuerst“:**
- Die App liest und schreibt immer in ihre **lokale Datenbank** – die Oberfläche wartet nie auf das Netz.
- Ein **Abgleich-Dienst** schickt lokale Änderungen zum Server und holt Änderungen anderer Geräte.
- Auslöser: App-Start, nach dem Speichern (mit kurzer Verzögerung), alle 15 Minuten im Hintergrund
  (Android WorkManager), manuell per „Jetzt abgleichen“.
- Der **Server ist die gemeinsame Wahrheit**; bei Konflikten entscheidet er (siehe Abschnitt 5).

**Technik Server:** reines PHP 8.1 ohne Framework (einfaches Hochladen, keine Abhängigkeiten), PDO mit
vorbereiteten Abfragen, JSON als Austauschformat. Ca. 6–8 PHP-Dateien.

---

## 4. Datenmodell

### 4.1 Änderungen gegenüber heute

| Heute | Neu | Grund |
|---|---|---|
| fortlaufende ID (1, 2, 3 …) | **UUID** pro Eintrag | zwei Geräte dürfen nicht dieselbe ID vergeben |
| Löschen entfernt den Eintrag | **Lösch-Merker** (`deleted_at`) | andere Geräte müssen vom Löschen erfahren |
| `updatedAt` vom Handy | zusätzlich **Server-Revision** (`rev`) | Abgleich „alles seit Revision X“, unabhängig von falschen Handy-Uhren |
| Anzahl Flaschen als Zahl | **Bestandsbuchungen** + abgeleiteter Bestand | gleichzeitige Änderungen gehen nicht verloren |
| — | `created_by`, `updated_by` | nachvollziehbar, wer was geändert hat |

### 4.2 Tabellen auf dem Server

```
users              id, name, email (optional), password_hash, role, active, created_at, last_login_at
sessions           id, user_id, token_hash, device_name, created_at, last_used_at, expires_at, revoked_at
login_attempts     id, user_name, ip, attempted_at, success

recipes            uuid, rev, name, category, glass, ice, method, garnish, instructions, notes, tags,
                   photo_id, is_favorite*, created_at, updated_at, updated_by, deleted_at
ingredients        uuid, recipe_uuid, position, name, amount, unit          (gehören fest zum Rezept)

bottles            uuid, rev, name, brand, category, size_ml, abv, barcode, price_net, label_text,
                   notes, photo_id, fill_percent, updated_at, updated_by, deleted_at
stock_movements    uuid, rev, bottle_uuid, delta, reason, created_at, created_by
                   reason: purchase | opened | correction | inventory

photos             id (UUID), sha256, size_bytes, mime, created_at, created_by
sync_state         rev (globaler Zähler, steigt bei jeder Änderung)
```

\* **Favoriten** sind persönlich und werden **pro Benutzer** gespeichert (`favorites: user_id, recipe_uuid`),
nicht am Rezept.

### 4.3 Bestand als Buchungen

Statt „Anzahl = 3“ speichert der Server Zu- und Abgänge:

| Aktion in der App | Buchung |
|---|---|
| Weitere Flasche (Anzahl +1) | `+1, purchase` |
| Leer – nächste öffnen | `-1, opened` (Füllstand → 100 %) |
| Anzahl mit −/+ geändert | `±n, correction` |
| Inventur-Zählung | `±Differenz, inventory` |

**Anzahl = Summe aller Buchungen.** Zählen zwei Personen gleichzeitig +1, ergibt das korrekt +2.
Nebeneffekt: ein **Verlauf** pro Flasche („wer hat wann nachgefüllt / geöffnet“).

Der **Füllstand** der angebrochenen Flasche bleibt ein absoluter Messwert (letzte Messung gewinnt) –
zwei gleichzeitige Messungen derselben Flasche sind ohnehin dieselbe Information.

---

## 5. Abgleich (Sync)

### 5.1 Ablauf

Ein einziger Aufruf pro Abgleich:

```
POST /api/sync
{
  "since": 1842,                         // letzte bekannte Server-Revision
  "changes": {
    "recipes":   [ { "uuid": "...", "base_rev": 1790, ...Felder... } ],
    "bottles":   [ ... ],
    "movements": [ { "uuid": "...", "bottle_uuid": "...", "delta": 1, "reason": "purchase" } ],
    "favorites": [ { "recipe_uuid": "...", "favorite": true } ]
  }
}

← 200
{
  "rev": 1857,                           // neue Revision → beim nächsten Mal als "since"
  "applied":   [ "uuid", ... ],
  "conflicts": [ { "uuid": "...", "server": { ...aktuelle Fassung... } } ],
  "changes":   { "recipes": [...], "bottles": [...], "movements": [...], "favorites": [...] }
}
```

- Jede lokale Änderung wird als „ausstehend“ markiert, bis der Server sie bestätigt.
- Große Datenmengen (Erstabgleich) werden seitenweise geholt (`limit` / `has_more`).

### 5.2 Konfliktregeln

| Daten | Regel |
|---|---|
| Rezepte | **Neueste Änderung gewinnt** (nach Server-Eingangszeit). Hat sich die Server-Fassung seit `base_rev` geändert, meldet der Server einen Konflikt; die App übernimmt die Server-Fassung und zeigt einen Hinweis „Rezept wurde zwischenzeitlich von *Name* geändert“. |
| Flaschen-Stammdaten (Name, Preis …) | wie Rezepte |
| Füllstand | letzte Messung gewinnt |
| Bestandsbuchungen | **kein Konflikt möglich** – Buchungen werden nur hinzugefügt |
| Löschen vs. Bearbeiten | Löschen gewinnt; der Admin kann gelöschte Einträge 30 Tage lang wiederherstellen |

Für eine einzelne Bar mit wenigen Geräten sind echte Konflikte selten; die Regeln sorgen dafür, dass dabei
nichts still verloren geht.

---

## 6. Benutzer und Rollen

| Rolle | Rezepte | Bestand | Preise | Benutzer | Inventur-CSV | Löschen |
|---|---|---|---|---|---|---|
| **Admin** (Inhaber, Barchef) | bearbeiten | bearbeiten | sehen, ändern | verwalten | ja | ja |
| **Bartender** | bearbeiten | bearbeiten | ausgeblendet¹ | – | – | nur eigene Entwürfe |
| **Lesen** (Aushilfe) | ansehen | ansehen | ausgeblendet | – | – | – |

¹ Einstellbar: Der Admin kann Bartendern die Preise freigeben.

- **Keine Selbstregistrierung.** Der Admin legt Konten an und erhält einen **Einladungscode** (einmalig, 7 Tage gültig),
  mit dem der Mitarbeiter sein Passwort selbst setzt.
- **Erster Admin** wird bei der Installation über ein einmaliges Einrichtungsskript angelegt, das sich danach selbst sperrt.
- **Konto sperren** beendet sofort alle Sitzungen dieses Kontos.
- **Geräteliste:** Der Admin sieht, welche Geräte angemeldet sind, und kann einzelne abmelden (z. B. verlorenes Handy).
- Verwaltung in der App (Bereich „Daten → Team“) – eine zusätzliche Browser-Oberfläche ist optional (Stufe 4).

---

## 7. Sicherheit

**Anmeldung**
- Passwörter nur als Hash (`password_hash`, Argon2id bzw. bcrypt), Mindestlänge 10 Zeichen.
- Nach der Anmeldung erhält das Gerät einen **Zugangsschlüssel** (256 Bit Zufall). Auf dem Server wird nur dessen
  Hash gespeichert; auf dem Handy liegt er im **Android Keystore** (verschlüsselter Speicher).
- Schlüssel laufen nach 90 Tagen Nichtbenutzung ab; Abmelden widerruft sie.
- **Schutz vor Passwort-Raten:** nach 5 Fehlversuchen 15 Minuten Sperre pro Benutzer und IP.

**Schnittstelle**
- Ausschließlich HTTPS; HTTP wird auf HTTPS umgeleitet, die App lässt kein HTTP zu.
- Alle Datenbankzugriffe über vorbereitete Abfragen (kein SQL aus Benutzereingaben).
- Jede Anfrage prüft Zugangsschlüssel **und** Rolle; Preise werden für Rollen ohne Preisrecht gar nicht ausgeliefert.
- Größen- und Wertegrenzen wie beim heutigen Import (Texte, Zahlenbereiche, max. Anfragegröße).
- Fehlermeldungen ohne interne Details; Fehler werden serverseitig protokolliert.

**Webspace**
- Zugangsdaten zur Datenbank in einer Datei **außerhalb** des öffentlichen Webverzeichnisses.
- Fotos ebenfalls außerhalb; Auslieferung nur über die Schnittstelle nach Anmeldung.
- Nur der Ordner `api/` ist öffentlich erreichbar, Verzeichnisauflistung abgeschaltet.
- Regelmäßige Datenbank-Sicherung (Anbieter + wöchentlicher Export per Cron, falls verfügbar).

---

## 8. Fotos

- Upload nach dem Speichern im Hintergrund (`PUT /api/photos/{uuid}`), max. 5 MB, nur JPEG/PNG/WebP,
  serverseitig auf echtes Bildformat geprüft.
- Fotos sind schon in der App verkleinert (max. 1600 px, ca. 200–400 KB).
- Andere Geräte laden Fotos erst, wenn sie angezeigt werden, und speichern sie zwischen.
- Gleiche Fotos werden über den SHA-256-Hash nur einmal gespeichert.

---

## 9. Datenschutz

- Neu gespeichert werden personenbezogene Daten der **Mitarbeiter**: Name, optional E-Mail, Anmeldezeiten,
  welche Geräte angemeldet sind, wer welche Änderung gemacht hat.
- Webspace bei einem Anbieter in der EU; **Vertrag zur Auftragsverarbeitung** mit dem Anbieter abschließen.
- Mitarbeiter über die Speicherung informieren (z. B. kurzer Hinweis beim ersten Anmelden).
- Löschfristen: Anmeldeversuche nach 30 Tagen, Sitzungen 90 Tage nach Ablauf, Konten auf Wunsch des Admins
  (Änderungen bleiben dann als „ehemaliger Mitarbeiter“ erhalten).
- Weiterhin keine Nutzungsstatistik, keine Werbung, keine weiteren Drittanbieter.

---

## 10. Umstellung bestehender Daten

1. App-Update mit neuer Datenbank-Version: Jeder vorhandene Eintrag bekommt eine UUID; die bisherige Anzahl
   wird in eine Anfangsbuchung (`inventory`) umgewandelt. Ohne Anmeldung arbeitet die App wie bisher weiter.
2. Der Admin meldet sich als Erster an und wählt **„Diesen Bestand als Startbestand hochladen“**.
3. Alle weiteren Geräte melden sich an und übernehmen den Server-Stand. Hat ein Gerät eigene Daten, fragt die
   App: **Server übernehmen** (lokal verwerfen, vorher automatische Sicherung) oder **zusammenführen**
   (gleichnamige Einträge zusammenlegen).
4. Vor jeder Umstellung erstellt die App automatisch eine Sicherung (ZIP).

---

## 11. Stufenplan

| Stufe | Inhalt | Ergebnis |
|---|---|---|
| **1 – Server-Grundlage** | Datenbank-Schema, Einrichtungsskript (erster Admin), Anmeldung, Sitzungen, Sync für Rezepte und Flaschen-Stammdaten, Tests gegen lokalen PHP-Server | Server läuft, per Testskript prüfbar |
| **2 – App verbinden** | UUIDs + Migration, Anmeldebildschirm, Abgleich-Dienst, Statusanzeige, Erstabgleich/Hochladen | Zwei Handys sehen dieselben Rezepte und Flaschen |
| **3 – Bestand und Fotos** | Bestandsbuchungen mit Verlauf, Füllstand-Sync, Foto-Upload/-Download | Bestand stimmt auf allen Geräten, Fotos überall |
| **4 – Team und Feinschliff** | Rollen, Preise ausblenden, Einladungscodes, Geräteliste, Konfliktanzeige, Wiederherstellen gelöschter Einträge, optional Browser-Verwaltung | Betrieb mit dem ganzen Team |

Jede Stufe ist für sich lauffähig und wird vor dem Hochladen auf den Webspace lokal getestet
(PHP-Testserver + MySQL auf dem Entwicklungsrechner bzw. Emulator).

**Rollenverteilung bei der Umsetzung:** Code (PHP und App) wird im Projekt erstellt und getestet. Datenbank
anlegen, Zugangsdaten eintragen, Dateien auf den Webspace laden und das HTTPS-Zertifikat aktivieren erfolgen
durch den Webspace-Inhaber – Zugangsdaten werden nicht weitergegeben.

---

## 12. Offene Fragen

1. Unter welcher Adresse soll die Schnittstelle laufen (Subdomain oder Unterordner)?
2. Gibt es auf dem Webspace Ordner außerhalb des öffentlichen Webverzeichnisses, SSH/SFTP und Cron-Jobs?
3. Sollen Bartender die Einkaufspreise sehen (Standard im Konzept: nein)?
4. Sollen Rezepte einen Freigabe-Status bekommen (z. B. „Entwurf“ / „auf der Karte“), den nur der Admin setzt?
5. Wird eine Browser-Oberfläche für den Barchef gewünscht (Rezepte am PC pflegen), oder reicht die App?
6. Wie viele Mitarbeiter und Geräte sind ungefähr geplant? (beeinflusst nur Kleinigkeiten wie Seitengrößen)
7. Soll die Einzelgeräte-Nutzung ohne Server dauerhaft möglich bleiben (Empfehlung: ja)?
