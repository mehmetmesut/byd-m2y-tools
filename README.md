<p align="center"><img src="docs/logo.png" width="128" height="128" alt="M2Y Board"></p>
<h1 align="center">M2Y Board</h1>
<p align="center"><b>A lean, offline Turkish Q keyboard built for the BYD multimedia screen</b><br><sub>BYD multimedya ekranı için sade, çevrim dışı Türkçe Q klavye · Лёгкая офлайн-клавиатура с турецкой раскладкой Q для мультимедийного экрана BYD</sub></p>
<p align="center">
  <a href="https://github.com/mehmetmesut/byd-m2y-board/releases/latest"><img alt="release" src="https://img.shields.io/github/v/release/mehmetmesut/byd-m2y-board?label=APK&color=2a78d6"></a>
  <a href="https://github.com/mehmetmesut/byd-m2y-board/releases"><img alt="downloads" src="https://img.shields.io/github/downloads/mehmetmesut/byd-m2y-board/total?color=1baf7a"></a>
  <img alt="Android 10" src="https://img.shields.io/badge/Android-10%20%28API%2029%29-3ddc84">
  <img alt="arm64" src="https://img.shields.io/badge/ABI-arm64--v8a-555">
  <img alt="no ads" src="https://img.shields.io/badge/ads-none-eb6834">
  <img alt="licence" src="https://img.shields.io/github/license/mehmetmesut/byd-m2y-board?color=6250d6">
</p>
<p align="center">
  <a href="https://github.com/mehmetmesut/byd-m2y-board/releases/download/1.25.07.68.1/M2YBoard_1.25.07.68.1.apk"><b>⬇ Download APK (4.3 MB)</b></a> ·
  <a href="https://byd.mehmetmesut.com">byd.mehmetmesut.com</a> ·
  <a href="https://github.com/mehmetmesut/byd-m2y">⭐ All M2Y apps for BYD</a>
</p>
<p align="center"><img src="docs/screenshots/1.jpg" width="80%" alt="M2Y Board on the BYD screen: Turkish Q layout with number row, suggestion strip and clipboard button in a search box"></p>

<details open>
<summary><b>🇬🇧 English</b></summary>

### Why this app

I built M2Y Board first for **my own BYD SEAL U DM-i** because the store apps did not fit the car: ads, Google Play dependencies, phone and TV layouts, and a heavy load on the CPU. The stock keyboard of DiLink 3.0 is built around Chinese input, and most keyboards from the stores need Google services or send what you type online. Then I made the app installable and usable on **BYD multimedia screens (DiLink)** and share it **free, without ads, without tracking**. It is built to use **as little of the head unit's CPU, RAM and storage as possible**: fast to open, smooth to touch, stable.

### Highlights

- **Turkish Q out of the box** – enabled and selected on a fresh install whatever the system language is; Russian (Cyrillic) layout and dictionary are bundled too.
- **100 % offline** – no internet permission, nothing is backed up, no learning: what you type is never stored.
- **Compact, centred keyboard** – uses the middle 55 % of the 1920×1080 screen; the sides stay transparent and touches there go to the app below.
- **Your own words one tap away** – a toolbar key adds the selected text or the word before the cursor (name, e-mail…) to the personal dictionary.
- **Small** – 4.3 MB APK, arm64 native libraries only.
- **No settings to fiddle with** – only languages, personal dictionary and about remain; everything else is fixed to tuned values.

### Features

- Turkish Q layout (ı, ğ, ü, ş, ö, ç, ₺) with Turkish dictionary and suggestions (about 1.2 MB dictionary).
- Russian (Cyrillic) layout with Russian dictionary (about 2.2 MB); switch it on in *Languages & layouts*.
- Number row always on; keyboard height 82 % of the default; equal gaps (half a key gap) around the outer keys.
- Bottom row: `?123` · `@` · space · `.` · action · hide keyboard. Long-press `.` for punctuation (comma is pre-selected, `*` is in the list).
- `?123` opens symbols, number pad and emoji as three tabs (`?123` · `#+=` · 😀) on their bottom row.
- E-mail domain suggestions after `@` (gmail.com, hotmail.com, outlook.com, icloud.com, yahoo.com, yandex.com).
- Personal dictionary with shortcuts for fixed words such as your name or e-mail; add words from the toolbar or in the settings.
- Toolbar: expand key with settings, clipboard, emoji, undo, redo, select word, copy, paste and cursor keys; add-word and clipboard keys are always visible next to the suggestions; swipe down on the toolbar to hide the keyboard.
- Clipboard history kept 10 minutes, pinned clips first; image and file copies are not written to disk.
- Next-word suggestions and fixed punctuation suggestions (`-` `@` `_`) on an empty strip.
- Removed to save resources and protect privacy: autocorrect, learning from typing, spell checker service, gesture typing and gesture-data collection, key sound and vibration, themes, one-handed and floating modes, in-app log and crash reports.

### Built for the head unit

| Head unit fact | Design decision |
|---|---|
| Android 10 (API 29), arm64-v8a, no Google services | No Google or Play dependency, no internet permission; the release APK carries arm64 native libraries only (4.3 MB) |
| 1920×1080 landscape touch screen, 240 dpi | Keyboard centred in 55 % of the width, transparent sides, height 82 %, number row always on |
| Turkish owner, occasional Russian | **Turkish Q** is enabled and selected on a fresh install regardless of the system language; Russian layout and dictionary bundled |
| Qualcomm SM6125, must never stutter | No learning, autocorrect, spell checker, gesture typing, key sound or vibration; emoji suggestions off; no clipboard file copies |
| Copy and paste while parked | Clipboard and add-word keys always visible on the toolbar |
| No voice input service | No microphone key |
| Privacy and a quiet settings screen | No data collection, no logs on disk; settings reduced to languages, personal dictionary and about |

### Install

On a head unit with wireless ADB enabled:

```
adb connect <car-ip>:5555
adb install -r M2YBoard_1.25.07.68.1.apk
adb shell ime enable com.m2y.board/helium314.keyboard.latin.LatinIME
adb shell ime set com.m2y.board/helium314.keyboard.latin.LatinIME
```

Instead of the two `ime` commands you can open M2Y Board from the launcher and follow its setup wizard, or enable the keyboard in the system's keyboard settings.

- **Windows one-click installer:** https://byd.mehmetmesut.com/m2y-kurulum.cmd – asks for the car's IP, downloads Google's platform-tools if missing, downloads the chosen apps, verifies SHA-256 and installs.
- **Direct install from the car's browser** only works on head units whose **stock BYD package installer has been unlocked** (the stock one blocks third-party APKs with a "driving safety" notice). This project does not distribute BYD files.
- All M2Y APKs are signed with the same M2Y key, so updates install over the previous version and keep your settings.
- After installing, the SealNav / stock launcher may need a restart to show the new icon.
- SHA-256 sums are listed at https://byd.mehmetmesut.com.

### Privacy & permissions

The app has **no internet permission** and `allowBackup` is off, so typed words and the clipboard history never leave the device and are not part of any backup. The manifest declares:

| Permission | Why |
|---|---|
| `READ_USER_DICTIONARY` / `WRITE_USER_DICTIONARY` | Read and write the system user dictionary (your own saved words) |
| `RECEIVE_BOOT_COMPLETED` | Lets the keyboard get ready after boot, after an app update and after a language change |
| `VIBRATE` | Declared by the upstream keyboard; key vibration is **off** and has no setting |

Never collected: no analytics, no crash reporting, no advertising, no tracking, no gesture data, no account.

### Compatibility

Designed and tested on the BYD SEAL U DM-i (DiLink 3.0). Other BYD models with an Android 10+ arm64 multimedia screen are expected to work but are not tested.

Target head unit: BYD DiLink 3.0 · Android 10 (API 29) · no Google services · Qualcomm SM6125 (8 cores, 4×1.8 + 4×2.0 GHz), Adreno 610 · 8 GB RAM · arm64-v8a · 1920×1080 landscape touch screen, 240 dpi, 60 Hz.

### Build from source

Requirements: JDK 17 or newer (CI uses 17), Android SDK (compileSdk 37), NDK 28.0.13004108.

```
./gradlew assembleRelease
```

The signed APK lands in `app/build/outputs/apk/release/`. Signing reads a properties file outside the repository (`m2y.signingProps` in `local.properties`). Release builds contain only the arm64-v8a libraries; debug builds also include x86_64 for emulator testing.

### Closing words

I made M2Y Board because typing in my own BYD SEAL U DM-i should be as comfortable as on a phone, without ads, without tracking and without asking the head unit for more than it can spare. If it makes your car a little nicer to use, I am glad. Questions and ideas are welcome as issues. If you like the project, please leave a ⭐ on the [hub repository](https://github.com/mehmetmesut/byd-m2y).

– **Yeşil Dönüşüm Mühendisi - M2Y**

</details>

<details>
<summary><b>🇹🇷 Türkçe</b></summary>

### Neden bu uygulama

M2Y Board'u önce **kendi BYD SEAL U DM-i aracım** için yaptım; çünkü mağaza uygulamaları araca uymuyordu: reklamlar, Google Play bağımlılıkları, telefon ve TV arayüzleri, işlemciyi yoran ağır yapı. DiLink 3.0'ın kendi klavyesi Çince girişe göre tasarlanmış, mağazadaki klavyelerin çoğu da Google hizmeti istiyor ya da yazdıklarınızı çevrimiçi gönderiyor. Sonra uygulamayı **BYD multimedya ekranlarında (DiLink)** kurulup kullanılabilir hâle getirdim ve **ücretsiz, reklamsız, izlemesiz** paylaşıyorum. Başlıca hedef, **başlık ünitesinin işlemcisini, RAM'ini ve depolamasını olabildiğince az kullanmak**: hızlı açılır, dokunuşa akıcı yanıt verir, kararlı çalışır.

### Öne çıkanlar

- **Kutudan çıktığı gibi Türkçe Q** – temiz kurulumda sistem dili ne olursa olsun açık ve seçili; Rusça (Kiril) düzen ve sözlük de pakette hazır.
- **%100 çevrim dışı** – internet izni yok, hiçbir şey yedeklenmez, öğrenme yok: yazdıklarınız hiçbir yerde saklanmaz.
- **Ortada, derli toplu klavye** – 1920×1080 ekranın ortadaki %55'ini kullanır; yanlar şeffaftır, oraya dokunuş alttaki uygulamaya gider.
- **Kendi kelimeleriniz tek dokunuşta** – araç çubuğundaki tuş seçili metni ya da imlecin solundaki kelimeyi (ad, e-posta…) kişisel sözlüğe ekler.
- **Küçük** – 4,3 MB APK, yalnızca arm64 yerel kitaplıkları.
- **Uğraştıran ayar yok** – yalnız diller, kişisel sözlük ve hakkında kaldı; geri kalan her şey ayarlanmış değerlerde sabit.

### Özellikler

- Türkçe Q düzeni (ı, ğ, ü, ş, ö, ç, ₺), Türkçe sözlük ve öneriler (sözlük yaklaşık 1,2 MB).
- Rusça (Kiril) düzen ve Rusça sözlük (yaklaşık 2,2 MB); *Diller ve Düzenler*'den açılır.
- Rakam satırı her zaman açık; klavye yüksekliği varsayılanın %82'si; dıştaki tuşların çevresinde eşit boşluk (tuş arası boşluğun yarısı).
- Alt satır: `?123` · `@` · boşluk · `.` · eylem · klavyeyi gizle. `.` tuşuna uzun basınca noktalama listesi (virgül hazır seçili, `*` listede).
- `?123` sembolleri, sayı tuş takımını ve emojiyi alt satırlarında üç sekme olarak açar (`?123` · `#+=` · 😀).
- `@` sonrasında e-posta alan adı önerileri (gmail.com, hotmail.com, outlook.com, icloud.com, yahoo.com, yandex.com).
- Adınız, e-postanız gibi sabit bilgiler için kısayollu kişisel sözlük; kelimeyi araç çubuğundan ya da ayarlardan ekleyin.
- Araç çubuğu: açma tuşunda ayarlar, pano, emoji, geri al, yinele, kelime seç, kopyala, yapıştır ve imleç tuşları; kelime ekle ve pano tuşları önerilerin yanında hep görünür; araç çubuğunda aşağı kaydırınca klavye gizlenir.
- Pano geçmişi 10 dakika tutulur, sabitlenenler önde; görsel ve dosya kopyaları diske yazılmaz.
- Sonraki kelime önerileri ve boş şeritte sabit noktalama önerileri (`-` `@` `_`).
- Kaynak ve gizlilik için kaldırılanlar: otomatik düzeltme, yazdıklarından öğrenme, yazım denetimi hizmeti, kaydırarak yazma ve hareket verisi toplama, tuş sesi ve titreşim, temalar, tek el ve yüzen klavye, uygulama içi günlük ve çökme raporları.

### Başlık ünitesi için tasarım

| Başlık ünitesi gerçeği | Tasarım kararı |
|---|---|
| Android 10 (API 29), arm64-v8a, Google hizmeti yok | Google ya da Play bağımlılığı yok, internet izni yok; sürüm APK'sı yalnızca arm64 yerel kitaplıkları içerir (4,3 MB) |
| 1920×1080 yatay dokunmatik ekran, 240 dpi | Klavye genişliğin %55'inde ortalı, yanlar şeffaf, yükseklik %82, rakam satırı her zaman açık |
| Türkçe konuşan sahip, ara sıra Rusça | **Türkçe Q** temiz kurulumda sistem dilinden bağımsız olarak açık ve seçili; Rusça düzen ve sözlük pakette |
| Qualcomm SM6125, asla takılmamalı | Öğrenme, otomatik düzeltme, yazım denetimi, kaydırarak yazma, tuş sesi ve titreşim yok; emoji önerisi kapalı; pano dosya kopyası yok |
| Park hâlinde kopyala-yapıştır | Pano ve kelime ekle tuşları araç çubuğunda her zaman görünür |
| Sesli giriş hizmeti yok | Mikrofon tuşu yok |
| Gizlilik ve sakin bir ayar ekranı | Veri toplama yok, diske günlük yok; ayarlar diller, kişisel sözlük ve hakkında ile sınırlı |

### Kurulum

Kablosuz ADB'nin açık olduğu bir başlık ünitesinde:

```
adb connect <araç-ip>:5555
adb install -r M2YBoard_1.25.07.68.1.apk
adb shell ime enable com.m2y.board/helium314.keyboard.latin.LatinIME
adb shell ime set com.m2y.board/helium314.keyboard.latin.LatinIME
```

İki `ime` komutu yerine M2Y Board'u başlatıcıdan açıp kurulum sihirbazını izleyebilir ya da klavyeyi sistemin klavye ayarlarından etkinleştirebilirsiniz.

- **Windows tek tıkla kurulum:** https://byd.mehmetmesut.com/m2y-kurulum.cmd – aracın IP adresini sorar, yoksa Google'ın platform-tools paketini indirir, seçilen uygulamaları indirir, SHA-256'yı doğrular ve kurar.
- **Aracın tarayıcısından doğrudan kurulum** yalnızca **özgün BYD paket yükleyicisinin kilidi açılmış** başlık ünitelerinde çalışır (özgün yükleyici üçüncü taraf APK'leri «sürüş güvenliği» uyarısıyla engeller). Bu proje BYD dosyası dağıtmaz.
- Tüm M2Y APK'leri aynı M2Y anahtarıyla imzalıdır; güncellemeler önceki sürümün üzerine kurulur ve ayarlarınız korunur.
- Kurulumdan sonra yeni simgenin görünmesi için SealNav / özgün başlatıcının yeniden başlatılması gerekebilir.
- SHA-256 toplamları https://byd.mehmetmesut.com adresinde listelenir.

### Gizlilik ve izinler

Uygulamada **internet izni yoktur** ve `allowBackup` kapalıdır; yazdığınız sözcükler ve pano geçmişi cihazdan çıkmaz, hiçbir yedeğe dâhil olmaz. Manifest şu izinleri bildirir:

| İzin | Neden |
|---|---|
| `READ_USER_DICTIONARY` / `WRITE_USER_DICTIONARY` | Sistem kullanıcı sözlüğünü (kendi kaydettiğiniz sözcükleri) okumak ve yazmak |
| `RECEIVE_BOOT_COMPLETED` | Klavyenin açılıştan, uygulama güncellemesinden ve dil değişikliğinden sonra hazır olmasını sağlar |
| `VIBRATE` | Üst projeden kalan bildirim; tuş titreşimi **kapalıdır** ve ayarı yoktur |

Hiçbir zaman toplanmaz: analitik, çökme raporu, reklam, izleme, hareket verisi ve hesap yoktur.

### Uyumluluk

BYD SEAL U DM-i (DiLink 3.0) üzerinde tasarlandı ve denendi. Android 10+ arm64 multimedya ekranlı diğer BYD modellerinde de çalışması beklenir, ancak denenmemiştir.

Hedef başlık ünitesi: BYD DiLink 3.0 · Android 10 (API 29) · Google hizmeti yok · Qualcomm SM6125 (8 çekirdek, 4×1,8 + 4×2,0 GHz), Adreno 610 · 8 GB RAM · arm64-v8a · 1920×1080 yatay dokunmatik ekran, 240 dpi, 60 Hz.

### Kaynaktan derleme

Gereksinimler: JDK 17 veya üzeri (CI 17 kullanır), Android SDK (compileSdk 37), NDK 28.0.13004108.

```
./gradlew assembleRelease
```

İmzalı APK `app/build/outputs/apk/release/` klasörüne çıkar. İmzalama, depo dışındaki bir özellik dosyasını okur (`local.properties` içindeki `m2y.signingProps`). Sürüm derlemeleri yalnızca arm64-v8a kitaplıklarını içerir; hata ayıklama derlemelerine emülatör denemesi için x86_64 de eklenir.

### Kapanış

M2Y Board'u, kendi BYD SEAL U DM-i aracımda yazı yazmak telefondaki kadar rahat olsun; reklam, izleme ve başlık ünitesinden gücünün fazlasını istemek olmasın diye yaptım. Aracınızı kullanmayı biraz daha keyifli kılarsa ne mutlu bana. Soru ve fikirlerinizi issue olarak iletebilirsiniz. Proje hoşunuza gittiyse [merkez depoya](https://github.com/mehmetmesut/byd-m2y) bir ⭐ bırakmanız beni çok sevindirir.

– **Yeşil Dönüşüm Mühendisi - M2Y**

</details>

<details>
<summary><b>🇷🇺 Русский</b></summary>

### Зачем это приложение

M2Y Board я сделал сначала для **собственного BYD SEAL U DM-i**, потому что приложения из магазинов не подходили автомобилю: реклама, зависимость от Google Play, интерфейсы для телефона и ТВ, большая нагрузка на процессор. Штатная клавиатура DiLink 3.0 рассчитана на ввод китайского текста, а большинство клавиатур из магазинов требуют сервисы Google или отправляют набранное в сеть. Затем я сделал приложение пригодным для установки и работы на **мультимедийных экранах BYD (DiLink)** и раздаю его **бесплатно, без рекламы, без слежки**. Главная цель — **как можно меньше загружать процессор, ОЗУ и память головного устройства**: быстрый запуск, плавный отклик на касания, стабильная работа.

### Главное

- **Турецкая раскладка Q из коробки** – при чистой установке включена и выбрана независимо от языка системы; русская (кириллическая) раскладка и словарь тоже входят в комплект.
- **100 % офлайн** – нет разрешения на интернет, ничего не попадает в резервные копии, нет обучения: набранный текст нигде не сохраняется.
- **Компактная клавиатура по центру** – занимает средние 55 % экрана 1920×1080; края прозрачные, касания там попадают в приложение под клавиатурой.
- **Свои слова в одно касание** – клавиша на панели добавляет выделенный текст или слово перед курсором (имя, e-mail…) в личный словарь.
- **Компактность** – APK 4,3 МБ, только нативные библиотеки arm64.
- **Никаких лишних настроек** – остались только языки, личный словарь и «О программе»; всё остальное зафиксировано на подобранных значениях.

### Возможности

- Турецкая раскладка Q (ı, ğ, ü, ş, ö, ç, ₺), турецкий словарь и подсказки (словарь около 1,2 МБ).
- Русская (кириллическая) раскладка и русский словарь (около 2,2 МБ); включается в разделе *Языки и раскладки*.
- Цифровой ряд всегда включён; высота клавиатуры 82 % от стандартной; одинаковые отступы (половина промежутка между клавишами) вокруг крайних клавиш.
- Нижний ряд: `?123` · `@` · пробел · `.` · действие · скрыть клавиатуру. Долгое нажатие на `.` — список знаков препинания (запятая выбрана сразу, `*` в списке).
- `?123` открывает символы, цифровую панель и эмодзи как три вкладки (`?123` · `#+=` · 😀) в их нижнем ряду.
- Подсказки доменов после `@` (gmail.com, hotmail.com, outlook.com, icloud.com, yahoo.com, yandex.com).
- Личный словарь с сокращениями для постоянных слов вроде имени или e-mail; слова добавляются с панели инструментов или в настройках.
- Панель инструментов: по кнопке раскрытия — настройки, буфер обмена, эмодзи, отмена, повтор, выделение слова, копирование, вставка и клавиши курсора; клавиши «добавить слово» и буфера всегда видны рядом с подсказками; свайп вниз по панели скрывает клавиатуру.
- История буфера обмена хранится 10 минут, закреплённые записи первыми; изображения и файлы на диск не записываются.
- Подсказки следующего слова и постоянные знаки препинания (`-` `@` `_`) на пустой строке подсказок.
- Удалено ради экономии ресурсов и конфиденциальности: автокоррекция, обучение на вводе, служба проверки орфографии, ввод жестами и сбор данных жестов, звук и вибрация клавиш, темы, режимы одной руки и плавающей клавиатуры, журнал в приложении и отчёты о сбоях.

### Создано для головного устройства

| Особенность головного устройства | Решение |
|---|---|
| Android 10 (API 29), arm64-v8a, нет сервисов Google | Нет зависимостей от Google и Play, нет разрешения на интернет; релизный APK содержит только нативные библиотеки arm64 (4,3 МБ) |
| Горизонтальный сенсорный экран 1920×1080, 240 dpi | Клавиатура по центру на 55 % ширины, прозрачные края, высота 82 %, цифровой ряд всегда включён |
| Владелец говорит по-турецки, иногда по-русски | **Турецкая раскладка Q** включена и выбрана при чистой установке независимо от языка системы; русская раскладка и словарь в комплекте |
| Qualcomm SM6125, никаких подтормаживаний | Нет обучения, автокоррекции, проверки орфографии, ввода жестами, звука и вибрации клавиш; подсказки эмодзи выключены; нет файловых копий буфера |
| Копирование и вставка на стоянке | Клавиши буфера и «добавить слово» всегда видны на панели инструментов |
| Нет службы голосового ввода | Нет клавиши микрофона |
| Конфиденциальность и спокойный экран настроек | Никакого сбора данных, никаких журналов на диске; настройки сведены к языкам, личному словарю и «О программе» |

### Установка

На головном устройстве с включённым беспроводным ADB:

```
adb connect <ip-автомобиля>:5555
adb install -r M2YBoard_1.25.07.68.1.apk
adb shell ime enable com.m2y.board/helium314.keyboard.latin.LatinIME
adb shell ime set com.m2y.board/helium314.keyboard.latin.LatinIME
```

Вместо двух команд `ime` можно открыть M2Y Board из лаунчера и пройти мастер настройки или включить клавиатуру в системных настройках клавиатуры.

- **Установщик для Windows в один клик:** https://byd.mehmetmesut.com/m2y-kurulum.cmd – запрашивает IP автомобиля, при необходимости скачивает platform-tools от Google, скачивает выбранные приложения, проверяет SHA-256 и устанавливает.
- **Прямая установка из браузера автомобиля** работает только на головных устройствах, у которых **разблокирован штатный установщик пакетов BYD** (штатный блокирует сторонние APK уведомлением о «безопасности движения»). Проект не распространяет файлы BYD.
- Все APK M2Y подписаны одним ключом M2Y, поэтому обновления устанавливаются поверх предыдущей версии и сохраняют настройки.
- После установки штатный лаунчер / SealNav может потребовать перезапуска, чтобы показать новый значок.
- Контрольные суммы SHA-256 приведены на https://byd.mehmetmesut.com.

### Конфиденциальность и разрешения

У приложения **нет разрешения на доступ в интернет**, а `allowBackup` выключен: набранные слова и история буфера обмена не покидают устройство и не попадают в резервные копии. В манифесте объявлены разрешения:

| Разрешение | Зачем |
|---|---|
| `READ_USER_DICTIONARY` / `WRITE_USER_DICTIONARY` | Чтение и запись системного пользовательского словаря (ваших сохранённых слов) |
| `RECEIVE_BOOT_COMPLETED` | Позволяет клавиатуре подготовиться после загрузки, после обновления приложения и после смены языка |
| `VIBRATE` | Унаследовано от исходной клавиатуры; вибрация клавиш **выключена** и не настраивается |

Не собирается никогда: нет аналитики, отчётов о сбоях, рекламы, слежки, данных жестов и учётных записей.

### Совместимость

Разработано и проверено на BYD SEAL U DM-i (DiLink 3.0). На других моделях BYD с мультимедийным экраном на Android 10+ (arm64) приложение, как ожидается, будет работать, но это не проверялось.

Целевое головное устройство: BYD DiLink 3.0 · Android 10 (API 29) · без сервисов Google · Qualcomm SM6125 (8 ядер, 4×1,8 + 4×2,0 ГГц), Adreno 610 · 8 ГБ ОЗУ · arm64-v8a · горизонтальный сенсорный экран 1920×1080, 240 dpi, 60 Гц.

### Сборка из исходников

Требуется: JDK 17 или новее (CI использует 17), Android SDK (compileSdk 37), NDK 28.0.13004108.

```
./gradlew assembleRelease
```

Подписанный APK появляется в `app/build/outputs/apk/release/`. Для подписи читается файл свойств вне репозитория (`m2y.signingProps` в `local.properties`). Релизные сборки содержат только библиотеки arm64-v8a; отладочные сборки дополнительно включают x86_64 для проверки в эмуляторе.

### Напоследок

Я сделал M2Y Board, чтобы набирать текст в моём BYD SEAL U DM-i было так же удобно, как на телефоне: без рекламы, без слежки и без требований к головному устройству сверх того, что оно может отдать. Если приложение сделает ваш автомобиль чуть удобнее, я буду рад. Вопросы и идеи приветствуются в issues. Если проект вам понравился, поставьте, пожалуйста, ⭐ в [центральном репозитории](https://github.com/mehmetmesut/byd-m2y).

– **Yeşil Dönüşüm Mühendisi - M2Y**

</details>

<hr>
<p align="center"><sub>M2Y Board is a fork of <a href="https://github.com/HeliBorg/HeliBoard">HeliBoard</a> and is licensed under the <b>GNU General Public License v3.0</b> (<a href="LICENSE">LICENSE</a>); M2Y changes © 2026 Yeşil Dönüşüm Mühendisi - M2Y under the same licence. HeliBoard by Helium314 and <a href="https://github.com/HeliBorg/HeliBoard/graphs/contributors">contributors</a>, based on <a href="https://github.com/openboard-team/openboard">OpenBoard</a> and the <a href="https://android.googlesource.com/platform/packages/inputmethods/LatinIME/">AOSP Keyboard</a> (Apache 2.0, see <a href="LICENSE-Apache-2.0">LICENSE-Apache-2.0</a>). Icon by <a href="https://github.com/FabianOvrWrt">Fabian OvrWrt</a> with contributions from <a href="https://github.com/the-eclectic-dyslexic">The Eclectic Dyslexic</a>, <a href="LICENSE-CC-BY-SA-4.0">CC BY-SA 4.0</a>. Also credited by HeliBoard: LineageOS, Simple Keyboard, Indic Keyboard, FlorisBoard. Dictionaries from <a href="https://codeberg.org/Helium314/aosp-dictionaries">Helium314/aosp-dictionaries</a>. Layout and theme documentation: <a href="https://github.com/HeliBorg/HeliBoard/wiki">HeliBoard wiki</a>.<br>Not affiliated with BYD; all trademarks belong to their owners. · © 2026 Yeşil Dönüşüm Mühendisi - M2Y</sub></p>
