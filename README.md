# Yapılacaklar (Android)

Kutlu Elektromekanik için kişisel iş listesi uygulaması.

- Kategori (İş, Proje, Şantiye, Teklif, Kişisel), öncelik (Acil / Normal / Düşük), not
- Son tarih + saat, telefona bildirimli hatırlatma (internet gerekmez)
- Bildirimden "Tamamlandı" ve "1 saat ertele"
- Telefon yeniden başlayınca hatırlatmalar kendiliğinden yeniden kurulur
- Arama, filtreler, silmede "Geri al"

## Derleme

`main` dalına her yükleme yapıldığında GitHub Actions APK'yı derler ve
**Releases** bölümüne `Yapilacaklar.apk` olarak koyar.

## İmza anahtarı

`app/release.keystore` güncellemelerin eski sürümün üzerine kurulabilmesi için
gerekir; silme veya değiştirme. Repoyu **Private** tut.
