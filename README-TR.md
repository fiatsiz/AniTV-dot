<p align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="resources/anitv-logo.svg">
    <img src="resources/anitv-logo-light.svg" alt="AniTV logo" width="420">
  </picture>
</p>

<h1 align="center">AniTV-dot</h1>
<p align="center"><a href="README.md">English</a> | <a href="README-TR.md">Türkçe</a></p>

Android TV üzerinden **AniTV-dot** ile anime izleyin.

*Bu araç, <a href="https://hianime.at/">hianime.</a> sitesinden veri toplar.*

## Özellikler

Proje geliştirme aşamasında, ancak aşağıdaki özellikler kullanılabilir durumda:

- Animeleri arama, favorilere ekleme ve izlendi olarak işaretleme.
- En son izlenen anime ve bölümlerini devam ettirme.
- Altyazıları açma/kapama, altyazı stilini değiştirme ve Google Translate ile DeepL aracılığıyla altyazıları diğer dillere çevirme ayarları (API anahtarları gerekli) (DeepL API test edildi ve çalışıyor; Google Translate API TEST EDİLMEDİ).

## Geliştirme Durumu

- Minimum SDK versiyonu: 34 (Android 14)
- Hedef SDK versiyonu: 37 (Android 17)

## İlham Kaynağı

Bu proje, anime izlemek için kullanılan bir komut satırı aracı olan [ani-cli](https://github.com/pystardust/ani-cli) projesinden ilham alınarak oluşturuldu. Android TV kullanıcıları için (özellikle de kendim için) benzer bir deneyim yaratmak istedim.

## Geliştirmede Kullanılan Yapay Zeka Araçları

<details>
  <summary>AI Kullanımı</summary>

  Bu proje benim bir hobi projem olduğu için, geliştirme sürecinde bana yardımcı olması amacıyla yapay zeka araçları kullanıyorum (zaten bu projeyi başlangıçta sadece anime izlemek amacıyla kendi kullanımım için yaptım). Ancak uygulamanın testlerini Android TV’mde yapıyorum ve kodu depoya göndermeden önce son bir gözden geçirme yapıyorum. Dolayısıyla, herhangi bir hata bulursanız lütfen bunu GitHub "Issues” bölümünde bildirin.
</details>

## Yasal Uyarı

AniTV-dot, bağımsız bir hobi projesidir. HiAnime, anime yaratıcıları, dağıtımcılar, Google veya DeepL ile herhangi bir bağlantısı bulunmamakta veya bu kuruluşlar tarafından desteklenmemektedir.

Uygulama, üçüncü taraflarca sağlanan içerik ve hizmetlere erişir. Bu proje, anime barındırmaz veya videolar, altyazılar, görseller ya da ticari markalar üzerinde herhangi bir hak vermez. Bu haklar, ilgili sahiplerine aittir. Bu uygulamayı kullanmanız ve üçüncü parti servislerinin içeriklerine erişmeniz tamamen sizin sorumluluğunuzdadır. Kişisel kurulum & kullanım, bu içeriği kopyalama veya yeniden dağıtma izni vermez.

Üçüncü taraf siteler ve hizmetler değişebilir, istekleri engelleyebilir veya kullanılamaz hale gelebilir. Arama, oynatma, altyazılar ve çeviri işlevleri çalışmayabilir. Altyazı çevirisini etkinleştirirseniz, uygulama sağladığınız API anahtarını kullanarak seçtiğiniz Google Cloud Translation veya DeepL hizmetine altyazı metnini gönderir.

Bu yazılım (AniTV-dot), [GPL-3.0](LICENSE) şartları uyarınca herhangi bir garanti olmaksızın sunulmaktadır.
