# Política de Privacidad — OptiPlay

**Última actualización: 14 de junio de 2026**

OptiPlay es un reproductor multimedia para Android desarrollado por EnMaNueL-G,
parte de la suite **OptiSuite** (https://optisuite.app).

## Resumen: OptiPlay NO recopila ningún dato.

OptiPlay está diseñado con privacidad absoluta. En concreto:

- **Sin acceso a Internet.** La aplicación **no declara el permiso `android.permission.INTERNET`**.
  Es técnicamente incapaz de enviar información a ningún servidor.
- **Sin telemetría ni analítica.** No incluye Firebase, Crashlytics, Google Analytics
  ni ningún SDK de seguimiento.
- **Sin publicidad.** No hay anuncios de ningún tipo.
- **Sin cuentas ni registro.** No necesitas iniciar sesión.
- **Tus datos nunca salen del dispositivo.** Biblioteca, listas de reproducción,
  favoritos, historial de reproducción y preferencias se guardan **únicamente en el
  almacenamiento local** del teléfono (base de datos Room y DataStore privados de la app).

## Permisos que utiliza y por qué

| Permiso | Para qué |
|---|---|
| `READ_MEDIA_AUDIO` / `READ_MEDIA_VIDEO` (Android 13+) | Leer tu música y vídeos para reproducirlos. |
| `READ_EXTERNAL_STORAGE` (Android 12 e inferior) | Igual que el anterior en versiones antiguas. |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Reproducir en segundo plano con notificación. |
| `POST_NOTIFICATIONS` (Android 13+) | Mostrar la notificación de reproducción con controles. |
| `WAKE_LOCK` | Mantener la reproducción mientras la pantalla está apagada. |

Ningún permiso se usa para recopilar, compartir o vender información.

## Datos almacenados localmente (solo en tu dispositivo)

- Índice de tu biblioteca de audio/vídeo (leído de MediaStore del sistema).
- Listas de reproducción que tú creas.
- Canciones marcadas como favoritas.
- Historial e contador de reproducciones (para "Recientes" y "Más reproducidas").
- Preferencias (tema, ecualizador, etc.).

Puedes borrar todo desinstalando la aplicación o borrando sus datos desde
Ajustes de Android.

## Contacto

¿Dudas? Escríbenos a **support@optisuite.app** o visita **https://optisuite.app**.

— EnMaNueL-G · OptiSuite
