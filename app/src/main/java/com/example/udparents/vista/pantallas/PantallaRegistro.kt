package com.example.udparents.vista.pantallas

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.udparents.viewmodel.VistaModeloUsuario
import kotlinx.coroutines.delay

@Composable
fun PantallaRegistro(
    viewModel: VistaModeloUsuario,
    onRegistroExitoso: () -> Unit,
    onIrAInicioSesion: () -> Unit
) {
    val usuario by viewModel.usuario.collectAsState()
    val mensaje by viewModel.mensaje.collectAsState()
    val cargando by viewModel.cargando.collectAsState()
    val contexto = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Regístrate",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )
        val nombreNormalizado = remember(usuario.nombre) {
            usuario.nombre.trim().replace("\\s+".toRegex(), " ")
        }
        val partes = nombreNormalizado.split(" ")
        val tieneNombreApellido = partes.size >= 2 && partes[0].length >= 2 && partes[1].length >= 2
        val largoOk = nombreNormalizado.replace(" ", "").length >= 10
        val nombreValido = tieneNombreApellido && largoOk


        OutlinedTextField(
            value = usuario.nombre,
            onValueChange = { viewModel.actualizarNombre(it) },
            label = { Text("Nombre y apellido") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrect = true,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            isError = usuario.nombre.isNotBlank() && !nombreValido,
            supportingText = {
                if (usuario.nombre.isNotBlank() && !nombreValido) {
                    Text("Escribe nombre y apellido (mín. 10 letras en total).")
                }
            }
        )


        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = usuario.correo,
            onValueChange = { viewModel.actualizarCorreo(it) },
            label = { Text("Correo electrónico") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = usuario.contrasena,
            onValueChange = { viewModel.actualizarContrasena(it) },
            label = { Text("Contraseña") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                viewModel.registrarUsuario { exito, _ ->
                    if (exito) {
                        Toast.makeText(contexto, "Registro exitoso", Toast.LENGTH_SHORT).show()
                        onRegistroExitoso()
                    }
                }
            },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF003366))
        ) {
            Text("Registrarse", color = Color.White)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onIrAInicioSesion) {
            Text("¿Ya tienes una cuenta? Inicia sesión")
        }

        Spacer(modifier = Modifier.height(16.dp))

        mensaje?.let {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = it,
                color = if (it.contains("✅")) Color(0xFF007F00) else Color.Red,
                fontSize = 14.sp
            )
        }
    }
}
