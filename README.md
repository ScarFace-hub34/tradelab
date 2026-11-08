# TradeLab (test amaçlı Fabric modu, Minecraft 26.2)

Gereksinim: Minecraft 26.2, Fabric Loader 0.19.5+, Fabric API 0.153.0+26.2, Java 25.
Derleme: Java 25 + Gradle 9.7.1 ile `gradle build` -> build/libs/tradelab-0.2.0.jar
(ya da GitHub Actions: .github/workflows/build.yml).

## Komutlar (tuş ataması yok)
- /tradelab        -> paneli açar
- /tradelab dump   -> sonraki açacağın menüyü 1 sn sonra loga döker (kalibrasyon)
- /tradelab stop   -> botu durdurur

## İlk kullanım
1. /tradelab dump yaz, sonra /ah glowstone ile menüyü aç; sohbette "Menü: ..." çıkmalı.
2. Aynısını /orders için yap; logs/latest.log içindeki satırları paylaş.
3. Ayarlar sekmesinden satış komutunu ve BotConfig.confirmItemId değerini sunucuya göre düzelt.

## Not
Derlenip test edilmedi. 26.x API adları (Mojang isimleri) araştırmaya göre yazıldı;
küçük derleme hataları çıkabilir. Order oluşturma akışı henüz yok.
