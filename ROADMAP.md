# Grab'it Roadmap

> Distribution: APK direct + stores alternatifs (Aptoide, Uptodown) + landing page
> Stack: Kotlin, Jetpack Compose, yt-dlp (youtubedl-android), FFmpeg, Room, Hilt

---

## Done

- [x] Core architecture (Hilt DI, Room DB, DataStore prefs, Foreground Service)
- [x] Share intent receiver (ACTION_SEND text/plain)
- [x] URL paste + manual input
- [x] yt-dlp extraction + metadata preview (thumbnail, title, duration, uploader)
- [x] Format picker (Best, 720p, 480p, Audio MP3)
- [x] Download via foreground service with notification progress
- [x] Download history (All / Active / Done tabs)
- [x] Dark + Light mode (Slate & Mint theme)
- [x] Custom typography (Inter + Space Mono)
- [x] Settings screen (download folder, quick mode, auto subfolder, clipboard monitor, dark mode)
- [x] SAF folder picker for download directory
- [x] Auto subfolder organization (Grabit/YouTube/, Grabit/Facebook/, Grabit/Audio/)
- [x] FFmpeg initialization (was missing, now fixed)
- [x] Pause / Resume downloads (process kill + --continue)
- [x] Swipe-to-delete on download cards
- [x] Delete button on failed downloads
- [x] Onboarding screen (first launch folder selection)
- [x] Auto-navigate back to home after starting download
- [x] Dark mode text visibility fixes across all screens
- [x] Progress clamping (no more -1%)

---

## Step 1: Tester le build actuel
> Priorite: MAINTENANT
> Effort: 10 min

- [x] 1.1 Build depuis Android Studio et installer sur device
- [x] 1.2 Verifier que FFmpeg init passe (Logcat: "FFmpeg initialized successfully")
- [x] 1.3 Tester download video YouTube (format Best)
- [x] 1.4 Tester download audio MP3
- [x] 1.5 Verifier que le fichier arrive dans Grabit/YouTube/ ou Grabit/Audio/
- [x] 1.6 Tester Pause puis Resume d'un download en cours
- [x] 1.7 Tester swipe-to-delete sur une card
- [x] 1.8 Tester l'onboarding (desinstaller l'app, reinstaller, verifier l'ecran de bienvenue)
- [x] 1.9 Tester share intent depuis YouTube app
- [x] 1.10 Verifier dark + light mode (tous les textes visibles)

---

## Step 2: Quick Mode
> Priorite: haute
> Effort: 30 min
> Dependance: Step 1 OK

- [x] 2.1 Dans HomeScreen, quand quick mode ON et user submit un lien: skip navigation vers Preview, creer le download direct en "best" et lancer le service
- [x] 2.2 Meme chose pour le share intent: si quick mode ON, pas de preview, download direct
- [x] 2.3 Afficher un petit toast/snackbar "Download started" au lieu de naviguer

---

## Step 3: Error Retry
> Priorite: haute
> Effort: 20 min

- [x] 3.1 Ajouter bouton Retry sur les cards FAILED (icone refresh)
- [x] 3.2 Le retry relance startDownload avec les memes parametres (comme resume)
- [x] 3.3 Remettre le status a QUEUED avant de relancer

---

## Step 4: Download Speed + ETA
> Priorite: moyenne
> Effort: 30 min

- [x] 4.1 Le callback yt-dlp donne deja progress + eta. Propager eta et speed jusqu'a la card
- [x] 4.2 Afficher sous la progress bar: "45% · 2.3 MB/s" a gauche, "1:32" a droite
- [x] 4.3 Formatter les unites proprement (KB/s, MB/s, secondes/minutes)

---

## Step 5: Notification Actions
> Priorite: moyenne
> Effort: 30 min

- [x] 5.1 Ajouter action "Pause All" dans la notification quand un download est actif
- [x] 5.2 ACTION_PAUSE_ALL pause tous les downloads et stoppe le service
- [ ] 5.3 Quand tous les downloads sont finis, notification "X downloads terminés" avec action "Open"

---

## Step 6: Clipboard Auto-Detect
> Priorite: moyenne
> Effort: 45 min

- [x] 6.1 Detecter via onWindowFocusChanged dans MainActivity (respecte restrictions Android 10+)
- [x] 6.2 Snackbar "Video link detected" avec bouton "Download" -> ouvre preview
- [x] 6.3 Respecter le toggle clipboardMonitor dans les settings
- [x] 6.4 Ne pas re-detecter le meme lien deux fois (lastClipboardText)

---

## Step 7: Format Picker Intelligent
> Priorite: haute
> Effort: 1h30
> C'est le "wow factor" de l'app

- [x] 7.1 Dans VideoExtractor, parser les vrais formats depuis le JSON yt-dlp (champ "formats")
- [x] 7.2 Extraire: resolution, ext, filesize, vcodec, acodec, tbr (bitrate)
- [x] 7.3 Grouper en categories: Video (4K, 1080p, 720p, 480p, 360p) + Audio (MP3, M4A, OPUS)
- [x] 7.4 Afficher la taille estimée a coté de chaque format ("720p MP4 - ~45 MB")
- [x] 7.5 Garder les formats "smart" (Best, Best 720p) en plus des formats bruts
- [x] 7.6 UI: bottom sheet ou section scrollable avec les formats groupés

---

## Step 8: Extraction Cache
> Priorite: moyenne
> Effort: 30 min

- [x] 8.1 Creer une table Room ou un cache en memoire (LRU) pour stocker VideoInfo par URL
- [x] 8.2 TTL de 1h (les liens expirent)
- [x] 8.3 Si l'URL est en cache et pas expirée, afficher le preview instantanement
- [x] 8.4 Bouton "Refresh" pour forcer la re-extraction

---

## Step 9: Multi-Download + Queue
> Priorite: haute
> Effort: 1h
> Gemini: limiter a 2-3 simultanés, yt-dlp gourmand CPU/RAM

- [x] 9.1 Dans DownloadService, maintenir un compteur de downloads actifs
- [x] 9.2 Si >= 3 actifs, les nouveaux restent en QUEUED
- [x] 9.3 Quand un download finit, lancer automatiquement le prochain en queue
- [x] 9.4 Afficher "In queue" sur la card pour les downloads en attente
- [ ] 9.5 Setting pour changer la limite (1, 2, 3 simultanés)

---

## Step 10: Support Playlists
> Priorite: moyenne
> Effort: 2h

- [x] 10.1 Detecter si l'URL est une playlist (youtube.com/playlist?list=...)
- [x] 10.2 Extraire la liste des videos avec yt-dlp --flat-playlist --dump-json
- [x] 10.3 Creer un ecran PlaylistScreen: liste des videos avec checkbox + thumbnail + titre
- [x] 10.4 Bouton "Download selected" qui cree un download pour chaque video cochée
- [x] 10.5 Option "Select all / Deselect all"

---

## Step 11: Browser Interne "Ghost"
> Priorite: basse (mais wow factor)
> Effort: 3h+
> Gemini: naviguer sur Instagram/LinkedIn, bouton flottant quand video detectée

- [ ] 11.1 Creer un ecran BrowserScreen avec WebView
- [ ] 11.2 Barre d'URL en haut, navigation back/forward
- [ ] 11.3 Intercepter les URLs de videos (regex sur les requetes reseau)
- [ ] 11.4 FAB "Download" flottant quand une video est detectée
- [ ] 11.5 Quick links: boutons raccourcis YouTube, Instagram, TikTok, Facebook
- [ ] 11.6 Mode incognito (pas de cookies sauvegardés)

---

## Step 12: Deep Link Handling
> Priorite: moyenne
> Effort: 30 min

- [ ] 12.1 Ajouter des intent-filters dans AndroidManifest pour youtube.com, youtu.be, instagram.com, tiktok.com, facebook.com, twitter.com, x.com
- [ ] 12.2 Quand l'user clique un lien video dans n'importe quelle app, Android propose "Ouvrir avec Grab'it"
- [ ] 12.3 Naviguer direct vers Preview (ou download direct si quick mode)

---

## Step 13: Lecteur Video Intégré
> Priorite: moyenne
> Effort: 2h

- [ ] 13.1 Ajouter ExoPlayer (Media3) comme dependance
- [ ] 13.2 Creer un ecran PlayerScreen avec controles (play/pause, seek, fullscreen)
- [ ] 13.3 Quand on clique une card COMPLETED, ouvrir le player
- [ ] 13.4 Support audio-only (afficher thumbnail + controles audio)
- [ ] 13.5 Gestion orientation (landscape pour video)

---

## Step 14: Streaming Pendant Download
> Priorite: basse
> Effort: 1h
> Dependance: Step 13

- [ ] 14.1 Quand un download est en cours, proposer "Watch while downloading"
- [ ] 14.2 ExoPlayer peut lire un fichier partiel si c'est du MP4 progressif
- [ ] 14.3 Fallback: streamer depuis l'URL originale pendant que le download continue

---

## Step 15: Picture-in-Picture (PiP)
> Priorite: basse
> Effort: 45 min
> Dependance: Step 13

- [ ] 15.1 Activer PiP dans AndroidManifest (android:supportsPictureInPicture="true")
- [ ] 15.2 Quand l'user quitte le player, passer en PiP automatiquement
- [ ] 15.3 Controles PiP: play/pause, next, close

---

## Step 16: Sous-titres
> Priorite: basse
> Effort: 45 min
> Dependance: Step 13

- [ ] 16.1 Option dans le format picker: "Download subtitles"
- [ ] 16.2 yt-dlp options: --write-subs --sub-langs "en,fr,ar"
- [ ] 16.3 Sauvegarder le .srt a coté de la video
- [ ] 16.4 ExoPlayer: charger les sous-titres automatiquement

---

## Step 17: Safe Zone Chiffrée
> Priorite: basse
> Effort: 2h
> Gemini: dossier protégé par biometrie, videos cachées de la galerie

- [ ] 17.1 Ajouter BiometricPrompt (androidx.biometric)
- [ ] 17.2 Creer un dossier chiffré avec EncryptedFile (AndroidX Security)
- [ ] 17.3 Option sur chaque download: "Move to Safe Zone"
- [ ] 17.4 Ecran Safe Zone accessible via biometrie uniquement
- [ ] 17.5 Les fichiers dans Safe Zone n'apparaissent pas dans la galerie

---

## Step 18: .nomedia + App Lock
> Priorite: basse
> Effort: 30 min

- [ ] 18.1 Option dans settings: "Hide from gallery"
- [ ] 18.2 Creer un fichier .nomedia dans Grabit/ quand activé
- [ ] 18.3 App lock: BiometricPrompt au lancement si activé dans settings

---

## Step 19: APK Signing + Play Protect Mitigation
> Priorite: haute (faire AVANT distribution)
> Effort: 45 min
> Context: Play Protect peut flagger l'app a cause des binaires yt-dlp/python embarqués.
> Ce n'est PAS bloquant (Seal, NewPipe, dvd ont le meme "probleme" et marchent).
> La lib junkfood02 utilise un interpréteur Python embarqué (pas Runtime.exec brut).

- [ ] 19.1 Creer un keystore de release (keytool) et configurer signingConfigs dans build.gradle
- [ ] 19.2 Activer R8/ProGuard en release (minifyEnabled=true, deja fait) pour obfusquer le code
- [ ] 19.3 Activer shrinkResources=true (deja fait) pour reduire la taille APK
- [ ] 19.4 Tester l'APK release signé sur un device avec Play Protect actif
- [ ] 19.5 Si Play Protect warning: ajouter un ecran "Installation Guide" sur la landing page expliquant comment autoriser l'install
- [ ] 19.6 Soumettre l'APK a Google via le formulaire "Appeal" si faussement flaggé (https://support.google.com/googleplay/android-developer/answer/2992033)
- [ ] 19.7 Optionnel: publier sur F-Droid (store open-source, pas de Play Protect issues)

---

## Step 20: Analytics + Crash Reporting (suivre les users)
> Priorite: haute (savoir combien on a d'users!)
> Effort: 45 min
> 100% GRATUIT. Deux options au choix:

### Option A: Firebase Analytics + Crashlytics (recommandé)
> Gratuit ILLIMITE pour toujours. Pas de carte bancaire.
> Seuls Database/Functions/Storage sont payants a grande echelle. Analytics+Crashes = free.

- [ ] 20.1 Creer un projet Firebase Console (console.firebase.google.com)
- [ ] 20.2 Ajouter google-services.json dans app/
- [ ] 20.3 Ajouter les dependances: firebase-analytics-ktx + firebase-crashlytics-ktx
- [ ] 20.4 Plugin Gradle: google-services + firebase-crashlytics

### Option B: Zero Google (si tu preferes)
> Alternatives 100% gratuites sans Google:

- [ ] 20.B1 **Aptabase** (open source, privacy-first, 20k events/mois gratuit)
- [ ] 20.B2 **Countly Community** (self-hosted, open source, illimite)
- [ ] 20.B3 **Simple ping maison**: endpoint Vercel Edge (gratuit) qui log juste: app_version + country + timestamp. Zero PII.

### Events a tracker (quelle que soit l'option):
- [ ] 20.5 `download_started` (source, format, isAudio)
- [ ] 20.6 `download_completed` (source, duration, fileSize)
- [ ] 20.7 `download_failed` (source, errorType)
- [ ] 20.8 `share_intent_received` (source)
- [ ] 20.9 Toggle "Send anonymous usage data" dans Settings (ON par defaut, desactivable)

### Ce que ca nous donne (gratuit):
- DAU / MAU (users actifs par jour/mois)
- Retention (qui revient)
- Crashes en temps reel
- Top sources, pays, devices

---

## Step 21: Auto-Update yt-dlp
> Priorite: haute (l'app casse sans ça)
> Effort: 30 min
> Gemini: les sites changent souvent leurs algos

- [ ] 21.1 On a deja YoutubeDL.updateYoutubeDL() au demarrage
- [ ] 21.2 Ajouter un bouton "Update engine" dans Settings
- [ ] 21.3 Afficher la version actuelle de yt-dlp dans Settings > About
- [ ] 21.4 Notification si une update est disponible

---

## Step 22: Auto-Update App (seamless, sans quitter l'app)
> Priorite: moyenne
> Effort: 1h30
> L'user ne quitte JAMAIS l'app. Il voit "Update dispo", tape, c'est fait.

- [ ] 22.1 Heberger le APK sur GitHub Releases avec un fichier version.json (version, url, changelog)
- [ ] 22.2 Au demarrage, fetch version.json via OkHttp/Ktor et comparer avec BuildConfig.VERSION_CODE
- [ ] 22.3 Si nouvelle version: afficher un dialog Material3 avec changelog + bouton "Update now"
- [ ] 22.4 Download l'APK en arriere-plan (reutiliser notre GrabitDownloadManager ou OkHttp direct)
- [ ] 22.5 Afficher progress bar dans le dialog pendant le download
- [ ] 22.6 Une fois telecharge, lancer PackageInstaller via intent (REQUEST_INSTALL_PACKAGES permission)
- [ ] 22.7 Ajouter permission dans Manifest: `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`
- [ ] 22.8 L'app se relance automatiquement apres l'install (Android gere ça)
- [ ] 22.9 Option "Remind me later" pour ne pas forcer l'update
- [ ] 22.10 Option "Skip this version" pour ignorer une version specifique

---

## Step 23: Support the Dev (Dons + Pub optionnelle)
> Priorite: moyenne
> Effort: 1h30
> Philosophie: app 100% GRATUITE, ZERO pub par defaut. L'user CHOISIT de soutenir.

### 23A: Section "Support" dans Settings
- [ ] 23.1 Nouvelle section dans SettingsScreen: "Support the developer"
- [ ] 23.2 Texte: "Grab'it is 100% free, no ads, no limits. If you find it useful, you can support its development."
- [ ] 23.3 Bouton "Buy me a coffee" -> lien Ko-fi (ko-fi.com, 0% commission sur les dons)
- [ ] 23.4 Bouton "Star on GitHub" -> lien repo (gratuit mais aide la visibilite)

### 23B: Pub optionnelle (rewarded ads)
- [ ] 23.7 Toggle dans Settings: "Show ads to support the developer" (OFF par defaut)
- [ ] 23.8 Texte sous le toggle: "100% optional. Grab'it will always be free and ad-free."
- [ ] 23.9 Provider: Unity Ads ou AppLovin MAX (marchent hors Play Store, contrairement a AdMob)
- [ ] 23.10 Type de pub: REWARDED VIDEO uniquement (pas de banners, pas de popups forcés)
  - L'user tape "Watch ad to support" -> pub de 15-30s -> message de remerciement
  - Jamais de pub forcée, jamais de pub surprise
- [ ] 23.11 Bouton "Watch an ad" dans la section Support (visible seulement si le toggle est ON)
- [ ] 23.12 Compteur fun: "You've supported us X times!" dans Settings

### 23C: Gratitude UX
- [ ] 23.13 Apres un don ou une pub: petit animation coeur/confetti + "Thank you!"
- [ ] 23.14 Badge discret "Supporter" visible quelque part dans l'app pour ceux qui ont contribué

### Revenue attendu (realiste):
- Ko-fi: 2-5 EUR par don, 0% commission
- Rewarded ads: ~0.01-0.03 EUR par vue (Unity Ads)
- 1000 users actifs qui regardent 1 pub/semaine = ~40-120 EUR/mois
- C'est du bonus, pas un business model. L'app reste gratuite quoi qu'il arrive.
- Badge "Supporter" pour ceux qui contribuent (don ou pub)

---

## Step 24: Landing Page
> Priorite: moyenne
> Effort: 2h (web)

- [ ] 24.1 One-pager moderne (Next.js ou HTML/CSS static)
- [ ] 24.2 Hero section avec screenshots de l'app (dark + light)
- [ ] 24.3 Feature highlights avec icones
- [ ] 24.4 Boutons: "Download APK" + "Aptoide" + "Uptodown"
- [ ] 24.5 Section FAQ (est-ce legal, quels sites supportés, etc.)
- [ ] 24.6 Deploy sur Vercel/Netlify

---

## Step 25: Premium UX Polish
> Priorite: basse
> Effort: 2h+

- [ ] 25.1 Animations de transition entre ecrans (shared element transitions)
- [ ] 25.2 Animation de chargement custom (pas le spinner basique)
- [ ] 25.3 Haptic feedback sur les actions (download start, complete, delete)
- [ ] 25.4 Widget home screen: champ URL + bouton download
- [ ] 25.5 Statistiques dans Settings: total downloads, espace utilisé, breakdown par source
- [ ] 25.6 Search bar dans la liste de downloads
- [ ] 25.7 Sort/filter (par date, taille, source, status)
- [ ] 25.8 Multi-select mode (long press pour selectionner, puis batch delete/share)

---

## Step 26: Backlog (quand on s'ennuie)
> Priorite: fun
> Effort: variable

- [ ] 26.1 Convertisseur video vers GIF (FFmpeg)
- [ ] 26.2 Video resize/compress avant partage
- [ ] 26.3 Partage direct vers WhatsApp/Telegram depuis l'app
- [ ] 26.4 Scheduled downloads (programmer pour la nuit en WiFi)
- [ ] 26.5 WiFi-only mode toggle
- [ ] 26.6 Storage manager (nettoyer vieux downloads, voir espace)
- [ ] 26.7 Metadata editor (renommer, changer thumbnail)
- [ ] 26.8 Audio equalizer dans le lecteur
- [ ] 26.9 Chromecast support (streamer vers TV)
- [ ] 26.10 Quick Settings tile (activer clipboard monitor)
- [ ] 26.11 Lecteur audio avec notification controls
- [ ] 26.12 Cloud backup (Google Drive / Dropbox)
