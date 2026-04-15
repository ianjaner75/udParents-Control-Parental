<h1 align="center">
  🛡️ udParents
</h1>

<p align="center">
  <strong>Mobile Parental Control System · Android · Cloud-Native</strong>
</p>

<p align="center">
  🚀 Seguridad · 📊 Monitoreo en Tiempo Real · ☁️ Arquitectura Escalable
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Firebase-FFCA28?style=for-the-badge&logo=firebase&logoColor=black"/>
  <img src="https://img.shields.io/badge/Cloud_Functions-FF6F00?style=for-the-badge&logo=googlecloud&logoColor=white"/>
  <img src="https://img.shields.io/badge/Node.js-43853D?style=for-the-badge&logo=node-dot-js&logoColor=white"/>
  <img src="https://img.shields.io/badge/MVVM-Architecture-orange?style=for-the-badge"/>
</p>

---

## 🧠 Visión del Proyecto

**udParents** es una plataforma avanzada de **control parental móvil** diseñada para entornos Android, enfocada en la **protección digital infantil** mediante monitoreo inteligente, control remoto y análisis de comportamiento en tiempo real.

> ⚡ Diseñado bajo principios de **arquitectura escalable, reactiva y orientada a eventos**.

> 🏆 **Reconocimiento:** Base de una ponencia internacional presentada en **CICOM 2025**.

---

## 🔥 Características Principales

- 📊 **Monitoreo en tiempo real** del uso de aplicaciones.
- ⏱️ **Control de tiempo dinámico** configurable desde la nube.
- 🔒 **Bloqueo remoto inmediato** de aplicaciones.
- 🔔 **Sistema de alertas push** en tiempo real.
- 📄 **Generación automática de reportes PDF**.
- 🔗 **Vinculación segura padre-hijo** mediante autenticación.

---
## 🏗️ Arquitectura del Sistema

El sistema implementa una arquitectura moderna basada en eventos:

- 📡 Comunicación **asíncrona en tiempo real**
- ☁️ Backend **serverless con Firebase**
- 🔄 Sincronización automática entre dispositivos

```kotlin
// Ejemplo: actualización remota de estado de bloqueo
val controlRef = firestore.collection("controls").document(childId)

controlRef.update("isBlocked", true)
    .addOnSuccessListener { 
        println("✅ Acceso bloqueado correctamente")
    }
    .addOnFailureListener { 
        println("❌ Error al aplicar restricción")
    }
```

---

## 🛠️ Stack Tecnológico

### 📱 Mobile (Frontend)
- **Lenguaje:** Kotlin  
- **UI:** Jetpack Compose  
- **Arquitectura:** MVVM  
- **Patrón:** Clean + separación por capas  

### ☁️ Backend & Cloud
- **Firestore:** Base de datos NoSQL en tiempo real  
- **Firebase Auth:** Autenticación segura (JWT)  
- **Cloud Functions:** Lógica de negocio serverless (Node.js)  
- **FCM:** Sistema de notificaciones push  

---

## ⚙️ Metodología de Desarrollo

El proyecto fue desarrollado bajo **SCRUM**, dividido en 5 sprints:

| Sprint | Objetivo |
|--------|----------|
| 1 | Autenticación y vinculación de usuarios |
| 2 | Motor de monitoreo de uso |
| 3 | Sistema de restricciones y bloqueos |
| 4 | Generación de reportes PDF |
| 5 | Optimización de notificaciones en tiempo real |

---

## 📊 Impacto y Valor

✔️ **Seguridad:** Reglas estrictas en Firestore  
✔️ **Baja latencia:** Sincronización casi instantánea  
✔️ **Escalabilidad:** Infraestructura serverless  
✔️ **Experiencia:** Interfaz moderna y fluida  

---

## 🚀 Diferencial Técnico

- Arquitectura **reactiva basada en eventos**  
- Integración completa con ecosistema Firebase  
- Separación clara de responsabilidades (**MVVM**)  
- Sistema extensible para futuras funcionalidades (IA, analítica, etc.)  

---

## 🎓 Contexto Académico

<p align="center">
  <i>Proyecto de Grado — Universidad Distrital Francisco José de Caldas · 2025</i>
</p>

---

## 💡 Próximas Mejoras

- 🤖 Integración con IA para detección de patrones de uso  
- 📊 Dashboard web para padres  
- 🔐 Control granular por tipo de contenido  
- 🌍 Soporte multi-dispositivo  

---

## 🤝 Contribuciones

Las contribuciones son bienvenidas.  
Si quieres mejorar el proyecto, abre un **issue** o envía un **pull request**.

---

<p align="center">
  ⭐ Si este proyecto te parece interesante, ¡no olvides darle una estrella!
</p>

