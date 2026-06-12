# Noctra Android — przewodnik krok po kroku

## 1. Zainstaluj Android Studio

Pobierz ze strony: https://developer.android.com/studio
Zainstaluj z domyślnymi ustawieniami. Przy pierwszym uruchomieniu przejdź przez Setup Wizard — pobierze Android SDK (~1 GB).

---

## 2. Otwórz projekt

1. Uruchom Android Studio
2. Kliknij **File → Open**
3. Nawiguj do: `C:\Users\promp\ScreenPal\noctra-android`
4. Kliknij OK
5. Poczekaj aż pasek na dole napisze **"Gradle sync finished"** (~2-5 min, pobiera zależności)

Jeśli pojawi się błąd "JDK not found" — idź do **File → Project Structure → SDK Location** i ustaw JDK Bundled with Android Studio.

---

## 3. Przygotuj urządzenie lub emulator

### Opcja A — Twój telefon (szybciej, zalecane)
1. Na telefonie: **Ustawienia → Informacje o telefonie → kliknij "Numer kompilacji" 7 razy**
2. Wróć do **Ustawienia → Opcje programisty → Debugowanie USB** — włącz
3. Podłącz telefon kablem USB
4. Gdy telefon zapyta "Zezwolić na debugowanie?" — kliknij Zezwól

### Opcja B — Emulator
W Android Studio: **Device Manager** (ikona telefonu po prawej) → **Create Device** → Pixel 8 → Android 14

---

## 4. Generuj pełną listę blokad

Otwórz PowerShell i wpisz:
```
cd C:\Users\promp\ScreenPal\noctra-android
node tools\build-blocklist.js
```
Wynik: ~100k domen w `app\src\main\assets\blocklist.txt` (ładuje reklamy jak EasyList).
Ten krok możesz pominąć — podstawowy stub działa od razu.

---

## 5. Uruchom aplikację

W Android Studio kliknij zielony przycisk ▶ **Run** (lub Shift+F10).
Aplikacja zainstaluje się na telefonie/emulatorze i uruchomi automatycznie.

---

## 6. Zbuduj APK do dystrybucji

**Build → Generate Signed App Bundle / APK → APK → Create new keystore**

Wypełnij:
- Keystore path: `C:\Users\promp\noctra-keystore.jks`
- Password: wymyśl swoje
- Alias: `noctra`

Kliknij Next → Release → Finish.
APK trafi do: `app\release\app-release.apk`

---

## Co robi aplikacja

| Funkcja | Jak używać |
|---|---|
| Nowa karta | Przycisk `+` w lewym górnym rogu |
| Zakładki | Gwiazdka ★ w pasku adresu |
| Shield (blokady) | Zielony chip `✕ 0` → liczba zablokowanych |
| Włącz/wyłącz shield na stronie | Chip shield → "disable shield on this site" |
| Profile | Menu `≡` → profiles |
| Ustawienia | Menu `≡` → settings |
| Poprzednia karta (zamknięta) | Menu `≡` → restore closed tab |
| Pobierania | Tray na dole, plik trafia do Pliki/Downloads |

---

## Jeśli coś nie działa

**"INSTALL_FAILED_UPDATE_INCOMPATIBLE"** — odinstaluj poprzednią wersję aplikacji z telefonu

**"Manifest merger failed"** — Gradle sync → Clean Project → Rebuild

**Brak gradlew** — Android Studio wygeneruje go automatycznie przy sync, nie potrzebujesz go ręcznie

**Aplikacja się crashuje** — View → Tool Windows → Logcat (filtry: "Error" lub "FATAL")
