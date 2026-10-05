package com.example.udparents.repositorio

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.example.udparents.modelo.Usuario
import com.google.firebase.auth.FirebaseUser

class RepositorioUsuario {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun registrarUsuario(
        usuario: Usuario,
        onResultado: (Boolean, String?) -> Unit
    ) {
        // Crea la cuenta en Firebase Auth
        auth.createUserWithEmailAndPassword(usuario.correo.trim(), usuario.contrasena)
            .addOnCompleteListener { tarea ->
                if (!tarea.isSuccessful) {
                    onResultado(false, tarea.exception?.message)
                    return@addOnCompleteListener
                }

                // No escribimos en Firestore aquí. Sólo enviamos el correo de verificación.
                val user = auth.currentUser
                if (user == null) {
                    onResultado(false, "No se pudo obtener el usuario actual tras el registro.")
                    return@addOnCompleteListener
                }

                // Enviar correo de verificación (usa Success/Failure, no Complete)
                user.sendEmailVerification()
                    .addOnSuccessListener {
                        // Envío aceptado por el backend → mostramos éxito
                        onResultado(true, null)
                    }
                    .addOnFailureListener { e ->
                        // Falló la solicitud de envío del correo
                        onResultado(false, "No se pudo enviar el correo de verificación: ${e.message}")
                    }
            }
    }

    fun iniciarSesion(usuario: Usuario, onResultado: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(usuario.correo.trim(), usuario.contrasena)
            .addOnCompleteListener { tarea ->
                if (!tarea.isSuccessful) {
                    onResultado(false, tarea.exception?.localizedMessage)
                    return@addOnCompleteListener
                }

                val usuarioFirebase = auth.currentUser
                if (usuarioFirebase == null) {
                    onResultado(false, "Usuario no encontrado.")
                    return@addOnCompleteListener
                }

                // Refresca el estado antes de leer isEmailVerified (por si acaba de verificar)
                usuarioFirebase.reload()
                    .addOnSuccessListener {
                        if (!usuarioFirebase.isEmailVerified) {
                            onResultado(false, "Debes verificar tu correo para iniciar sesión.")
                            return@addOnSuccessListener
                        }

                        // Email verificado → asegurar documento en Firestore
                        val uid = usuarioFirebase.uid
                        val ref = db.collection("usuarios").document(uid)

                        ref.get()
                            .addOnSuccessListener { snap ->
                                if (snap.exists()) {
                                    // Ya hay un perfil → continuar
                                    onResultado(true, null)
                                } else {
                                    // Primer login verificado → crear perfil
                                    val datos = mapOf(
                                        "nombre" to usuario.nombre.trim(),      // usa el nombre que tenemos en memoria
                                        "correo" to usuario.correo.trim(),
                                        "createdAt" to System.currentTimeMillis(),

                                    )
                                    ref.set(datos)
                                        .addOnSuccessListener { onResultado(true, null) }
                                        .addOnFailureListener { e ->
                                            onResultado(false, "Error guardando perfil del usuario: ${e.message}")
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                onResultado(false, "Error al verificar perfil del usuario: ${e.message}")
                            }
                    }
                    .addOnFailureListener { e ->
                        onResultado(false, "No se pudo actualizar el estado del usuario: ${e.message}")
                    }
            }
    }

    fun recuperarContrasena(usuario: Usuario, onResultado: (Boolean, String?) -> Unit) {
        auth.sendPasswordResetEmail(usuario.correo)
            .addOnCompleteListener { tarea ->
                if (tarea.isSuccessful) {
                    onResultado(true, null)
                } else {
                    onResultado(false, tarea.exception?.localizedMessage)
                }
            }
    }
    fun obtenerUsuarioActual(): FirebaseUser? {
        return auth.currentUser
    }
    fun obtenerEstadoAlertaContenido(
        uid: String,
        onResultado: (Boolean?) -> Unit
    ) {
        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { documento ->
                onResultado(documento.getBoolean("alertaContenidoProhibido"))
            }
            .addOnFailureListener {
                onResultado(null)
            }
    }

    fun actualizarEstadoAlertaContenido(
        uid: String,
        nuevoEstado: Boolean,
        onResultado: (Boolean) -> Unit
    ) {
        db.collection("usuarios").document(uid)
            .update("alertaContenidoProhibido", nuevoEstado)
            .addOnSuccessListener { onResultado(true) }
            .addOnFailureListener { onResultado(false) }
    }

}
