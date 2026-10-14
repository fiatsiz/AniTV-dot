<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="resources/anitv-logo.svg">
    <img src="resources/anitv-logo-light.svg" alt="AniTV logo" width="420">
  </picture>
</p>

<h1 align="center">AniTV-dot</h1>
<p align="center"><a href="README.md">English</a> | <a href="README-TR.md">Türkçe</a></p>

Android TV üzerinden **AniTV-dot** ile anime izleyin.

*Bu araç, <a href="https://hianime.at/">hianime</a> sitesinden veri toplar.*

## Özellikler

Proje geliştirme aşamasında, ancak aşağıdaki özellikler kullanılabilir durumda:

- Animeleri arama, favorilere ekleme ve izlendi olarak işaretleme.
- En son izlenen anime ve bölümlerini devam ettirme.
- Altyazıları açma/kapama, altyazı stilini değiştirme ve Google Translate ile DeepL aracılığıyla altyazıları diğer dillere çevirme ayarları (API anahtarları gerekli) (DeepL API test edildi ve çalışıyor; Google Translate API TEST EDİLMEDİ).

## Geliştirme Durumu

- Minimum SDK versiyonu: 34 (Android 14)
- Hedef SDK versiyonu: 37 (Android 17)

## Kurulum ve sürümler

[AniTV 1.1.0 sürümünden](https://github.com/fiatsiz/AniTV-dot/releases/tag/v1.1.0) `AniTV-1.1.0.apk` dosyasını indirin. Android 14 veya daha yeni Android TV / Google TV gereklidir. VersionName `1.1.0`, Android versionCode `2` değerindedir.

Sürümün `SHA256SUMS` dosyasını ve imza sertifikasını kontrol edin, ardından APK'yı TV'ye kopyalayıp seçtiğiniz APK yükleyicisiyle kurun. 1.0.0'da APK güncelleyici bulunmadığından 1.1.0'ı elle kurun. İki sürüm de aynı uygulama sahibi sertifikasıyla imzalanmıştır; uygulamayı kaldırmadan yerinde güncelleme yapılabilir. Farklı sertifikayla imzalanmış debug kurulumu genellikle yerinde güncellenemez; kaldırmak favorileri ve ilerlemeyi silebilir. İmza hatasını aşmak için verileri temizlemeyin veya uygulamayı kaldırmayın.

1.1.0'dan itibaren AniTV, son denemeden bu yana 24 saat geçtiğinde açılışta veya uygulamaya dönüşte GitHub'daki daha yeni kararlı sürümleri denetler. Ayarlar'dan elle de denetleyebilirsiniz. Ana ekrandaki bildirim, Ayarlar'daki güncelleme ayrıntılarını açar. İndirme yalnızca Güncelle'yi seçtiğinizde başlar; ilerleme gösterilir ve iptal edilebilir. AniTV arka plana geçtiğinde tamamlanmamış indirmeler durdurulur. Kurulum için Android'in uygulama yükleme izni ve onayı gerekir. AniTV kapalıyken denetim yapmaz veya sessiz kurulum gerçekleştirmez. İmzalı HiAnime ayrıştırma kuralları, herkese açık [rules dalından](https://github.com/fiatsiz/AniTV-dot/tree/rules) ayrı olarak güncellenmeye devam eder.

Sürüm, eşleşen GPL-3.0 kaynak kodunu, [lisansı](LICENSE), `RELEASE-INFO.txt` ve `SHA256SUMS` dosyalarını içerir. İmzalar ve test edilen/edilmeyen davranışlar için [doğrulama raporuna](docs/RELEASE-VERIFICATION.md), yerel derleme için [sürüm kılavuzuna](docs/RELEASE.md) bakın.

## İlham Kaynağı

Bu proje, anime izlemek için kullanılan bir komut satırı aracı olan [ani-cli](https://github.com/pystardust/ani-cli) projesinden ilham alınarak oluşturuldu. Android TV kullanıcıları için (özellikle de kendim için) benzer bir deneyim yaratmak istedim.

## Geliştirmede Kullanılan Yapay Zeka Araçları

<details>
  <summary>AI Kullanımı</summary>

  Bu proje benim bir hobi projem olduğu için, geliştirme sürecinde bana yardımcı olması amacıyla yapay zeka araçları kullanıyorum (zaten bu projeyi başlangıçta sadece anime izlemek amacıyla kendi kullanımım için yaptım). Ancak uygulamanın testlerini Android TV’mde yapıyorum ve kodu depoya göndermeden önce son bir gözden geçirme yapıyorum. Dolayısıyla, herhangi bir hata bulursanız lütfen bunu GitHub "Issues” bölümünde bildirin.
</details>

## Yasal Uyarı

AniTV-dot, bağımsız bir hobi projesidir. HiAnime, anime yaratıcıları, dağıtımcılar, Google veya DeepL ile herhangi bir bağlantısı bulunmamakta veya bu kuruluşlar tarafından desteklenmemektedir.

Uygulama, üçüncü taraflarca sağlanan içerik ve hizmetlere erişir. Bu proje, medya barındırmaz veya videolar, altyazılar, görseller ya da ticari markalar üzerinde herhangi bir hak vermez. Bu haklar, ilgili sahiplerine aittir. Bu uygulamayı kullanmanız ve üçüncü parti servislerinin içeriklerine erişmeniz tamamen sizin sorumluluğunuzdadır. Kişisel kurulum & kullanım, bu içeriği kopyalama veya yeniden dağıtma izni vermez.

Üçüncü taraf siteler ve hizmetler değişebilir, istekleri engelleyebilir veya kullanılamaz hale gelebilir. Arama, oynatma, altyazılar ve çeviri işlevleri çalışmayabilir. Altyazı çevirisini etkinleştirirseniz, uygulama sağladığınız API anahtarını kullanarak seçtiğiniz Google Cloud Translation veya DeepL hizmetine altyazı metnini gönderir.

Bu yazılım (AniTV-dot), [GPL-3.0](LICENSE) şartları uyarınca herhangi bir garanti olmaksızın sunulmaktadır.
