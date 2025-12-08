# Notification Manager - Android

Sistema de gestión y reenvío de notificaciones Android a dispositivos ESP32 mediante MQTT y Bluetooth.

## 🎯 Características

- ✅ **Interceptación de Notificaciones**: Captura notificaciones de apps seleccionadas
- ✅ **Vinculación Bluetooth**: Descubrimiento y pairing con ESP32 cercanos
- ✅ **Seguridad por Token**: Token de 8 caracteres como topic MQTT privado
- ✅ **MQTT Liviano**: Protocolo eficiente para comunicación IoT
- ✅ **Arquitectura Limpia**: SOLID, DRY, Clean Architecture
- ✅ **Battery Optimizado**: Escaneo Bluetooth consciente del estado de batería

## 📋 Requisitos Previos

### Hardware
- Dispositivo Android con Bluetooth (API 24+)
- ESP32 con pantalla LCD y buzzer

### Softwarepara Configurar
- Android Studio Hedgehog o superior
- JDK 17
- Gradle 8.2+

## 🚀 Configuración

### 1. Clonar Repositorio

```bash
git clone https://github.com/tuusuario/NotificationManager.git
cd NotificationManager
```

### 2. Configurar Credenciales MQTT

Crea `local.properties` en la raíz del proyecto:

```bash
cp local.properties.template local.properties
```

Edita `local.properties` con tus credenciales MQTT:

```properties
mqtt.broker=ssl://tu-broker.emqxsl.com:8883
mqtt.username=tu_usuario
mqtt.password=tu_contraseña
```

> ⚠️ **IMPORTANTE**: `local.properties` está en `.gitignore`. Nunca commitees credenciales.

### 3. Sync y Build

```bash
./gradlew clean
./gradlew assembleDebug
```

## 📱 Permisos Requeridos

La app solicita los siguientes permisos:

- **Notification Listener** (manual): Para interceptar notificaciones
- **POST_NOTIFICATIONS** (runtime): Para notificaciones propias (Android 13+)
- **BLUETOOTH_SCAN** (runtime): Para descubrir dispositivos (Android 12+)
- **BLUETOOTH_CONNECT** (runtime): Para conectar con ESP32 (Android 12+)

## 🔌 Configuración ESP32

### Hardware Connections
```
LCD I2C:
- SDA → GPIO 21
- SCL → GPIO 22

Buzzer:
- Pin → GPIO 12

Botón Desvincular:
- Pin → GPIO 13 (pull-up interno)
```

### Firmware

Carga el firmware desde `app/src/main/java/com/dynamictecnologies/notificationmanager/codigoarduino.txt`:

```cpp
// Librerías requeridas:
// - WiFi (built-in)
// - PubSubClient
// - LiquidCrystal_I2C
// - ArduinoJson
// - Preferences (built-in)
```

Configura tu WiFi en el código Arduino:

```cpp
#define WIFI_SSID "TU_WIFI"
#define WIFI_PASSWORD "TU_PASSWORD"
```

## 🔗 Flujo de Vinculación

### Primera Vez

1. **ESP32**: Enciende → LCD muestra `TOKEN: XXXXXXXX`
2. **App**: Abre NotificationManager
3. **App**: Otorga permisos de Notification Listener
4. **App**: Click "Buscar dispositivos ESP32"
5. **App**: Otorga permisos Bluetooth cuando se solicite
6. **App**: Selecciona tu ESP32 de la lista
7. **App**: Ingresa el token de 8 caracteres mostrado en LCD
8. **ESP32**: LCD cambia a "Vinculado" + melodía de confirmación

### Desvincular

**Opción A** - Desde App:
- Click botón "Desvincular" en card del dispositivo

**Opción B** - Desde ESP32:
- Mantén presionado botón GPIO13 por 3 segundos
- ESP32 genera nuevo token y muestra en LCD

## 🏗️ Arquitectura

```
app/
├── domain/              # Entidades y lógica de negocio
│   ├── entities/        # DevicePairing, Exceptions
│   ├── repositories/    # Interfaces
│   └── usecases/        # Business logic
├── data/                # Implementaciones
│   ├── repository/      # Repository implementations
│   ├── bluetooth/       # BluetoothDeviceScanner
│   ├── mqtt/            # MqttConnectionManager, Publisher
│   ├── dto/             # Data Transfer Objects
│   ├── cleanup/         # NotificationCleanupService
│   └── permissions/     # PermissionChecker
├── presentation/        # UI Layer
│   ├── home/           
│   └── components/      # Composables
├── service/             # Android Services
│   └── recovery/        # Service recovery components
├── util/                # Utilities
│   ├── network/         # NetworkConnectivityChecker
│   └── security/        # RateLimiter
└── di/                  # Dependency Injection
```

### Principios Aplicados
- **SOLID**: Todos los componentes con responsabilidad única
- **Clean Architecture**: Dependency Rule estricta
- **DRY**: Sin duplicación de código
- **Security**: Input validation, rate limiting, DTOs

## 🧪 Testing

### Ejecutar Tests Unitarios

```bash
./gradlew testDebugUnitTest
```

### Cobertura de Tests

- `TokenValidatorTest`: 8 test cases (100% cobertura)
- `PairDeviceWithTokenUseCaseTest`: 7 test cases
- `SendNotificationToDeviceUseCaseTest`: 4 test cases

## 🔐 Seguridad

### Implementado

✅ **Token-based Security**: 36^8 = 2.8 trillion combinaciones  
✅ **Input Validation**: Validación estricta en DevicePairing  
✅ **Rate Limiting**: Máximo 10 notificaciones/minuto  
✅ **DTOs**: Límites de tamaño (título: 100 chars, contenido: 500 chars)  
✅ **BuildConfig**: Credenciales fuera del código fuente  

### Mejoras Futuras

- SSL Certificate Pinning para MQTT
- Encriptación de payloads (opcional)
- Autenticación biométrica para unpairing

## 📊 Limitaciones Conocidas

- **Single Device**: Solo 1 ESP32 vinculado a la vez
- **Local Only**: Sin sincronización en la nube
- **No Multi-User**: Diseñado para uso personal

> Estas limitaciones son intencionales para simplificar el MVP. Ver roadmap para expansión futura.

## 🛠️ Troubleshooting

### "MQTT no conecta"
- Verifica credenciales en `local.properties`
- Confirma broker MQTT está accesible
- Revisa logs: `adb logcat -s MqttConnectionManager`

### "No recibo notificaciones en ESP32"
- Verifica ESP32 muestre "Vinculado" en LCD
- Confirma app tiene permisos de Notification Listener
- Revisa logs: `adb logcat -s NotificationListenerService`

### "No encuentro ESP32 en escaneo Bluetooth"
- Asegura ESP32 esté encendido y conectado a WiFi
- Otorga permisos Bluetooth a la app
- El nombre debe empezar con "ESP32"

## 📝 Conventional Commits

Este proyecto usa Conventional Commits para mensajes claros:

```
feat: agregar vinculación SSL pinning
fix: corregir rate limiting en notificaciones
docs: actualizar README con instrucciones ESP32
refactor: separar NotificationRepository en componentes
test: agregar tests para TokenValidator
```

## 🤝 Contribuir

1. Fork el proyecto
2. Crea feature branch (`git checkout -b feature/nueva-funcionalidad`)
3. Commit cambios (`git commit -m 'feat: agregar nueva funcionalidad'`)
4. Push al branch (`git push origin feature/nueva-funcionalidad`)
5. Abre Pull Request

## 📄 Licencia

Este proyecto es de código abierto bajo licencia MIT.

## 👨‍💻 Autor

**RedDeadth**

## 🙏 Agradecimientos

- Clean Architecture por Robert C. Martin
- MQTT Protocol (OASIS)
- Android Compose Team
- ESP32 Community

---

**¿Preguntas?** Abre un issue en GitHub
