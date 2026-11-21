# Escáner de texto — versión Android

Este proyecto genera el APK de la app. Funciona sin internet: el motor OCR y los idiomas
(español e inglés) van dentro de la app.

## Obtener el APK sin instalar nada (GitHub, gratis, ~5 minutos)

1. Crea una cuenta en https://github.com si no tienes.
2. Arriba a la derecha: **+ → New repository**. Ponle un nombre (por ejemplo `escaner-texto`),
   déjalo en **Public** o **Private** y pulsa **Create repository**.
3. En la página del repositorio nuevo, pulsa **uploading an existing file**.
   Descomprime este ZIP y arrastra **todo su contenido** (las carpetas `android`, `www`, `.github`
   y los archivos sueltos). Pulsa **Commit changes**.
4. Revisa que exista el archivo `.github/workflows/build-apk.yml`. Algunos sistemas no suben
   carpetas que empiezan con punto. Si falta: pestaña **Actions → set up a workflow yourself**,
   pega el contenido de `build-apk.yml` (hay una copia en la raíz de esta carpeta: `build-apk.yml`)
   y pulsa **Commit changes**.
5. Ve a la pestaña **Actions**. Verás «Construir APK» ejecutándose (círculo amarillo).
   Cuando termine (marca verde, unos 4–6 minutos), entra en ella y, abajo, en **Artifacts**,
   descarga **EscanerDeTexto-APK**. Es un ZIP que contiene `EscanerDeTexto-1.1.0.apk`.

## Instalar en el teléfono

1. Pasa el archivo `.apk` al teléfono (por cable, WhatsApp a ti mismo, Google Drive, etc.).
2. Ábrelo. Android pedirá permitir **instalar apps de origen desconocido** para esa aplicación
   (Archivos, Chrome, Drive…). Acepta y pulsa **Instalar**.
3. Si aparece Play Protect avisando de una «app desconocida», elige **Instalar de todas formas**.
   El aviso sale porque la app no viene de Play Store.

## Leer texto de cualquier parte de la pantalla

1. Haz una captura de pantalla del teléfono (normalmente **Encendido + Bajar volumen**).
2. En la vista previa de la captura (o desde la Galería) pulsa **Compartir → Escáner de texto**.
3. La app se abre con la captura: marca con el dedo la zona que quieres leer, toca **Escanear**
   y luego **Copiar texto**.

## Compilar en tu PC (opcional, si tienes Android Studio)

```
npm install
npx cap sync android
npx cap open android      # luego Build → Build APK(s)
```

## Estructura

- `www/` — la app (HTML) y el motor OCR local (`lib/`, `core/`, `lang/`).
- `android/` — proyecto Android generado con Capacitor 6.
- `.github/workflows/build-apk.yml` — compila el APK automáticamente en GitHub.
