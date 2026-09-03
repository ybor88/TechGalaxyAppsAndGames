package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.ClienteViewModel
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpostazioniScreen(
    sessionViewModel: SessionViewModel,
    clienteViewModel: ClienteViewModel,
    onOpenDrawer: () -> Unit
) {
    val user by sessionViewModel.user.collectAsState()

    var nome by remember { mutableStateOf(user?.nome ?: "") }
    var cognome by remember { mutableStateOf(user?.cognome ?: "") }
    var email by remember { mutableStateOf(user?.email ?: "") }
    var telefono by remember { mutableStateOf(user?.telefono ?: "") }

    var passwordAttuale by remember { mutableStateOf("") }
    var nuovaPassword by remember { mutableStateOf("") }
    var confermaPassword by remember { mutableStateOf("") }

    val loading by clienteViewModel.loading.collectAsState()
    val profiloError by clienteViewModel.profiloError.collectAsState()
    val profiloSuccesso by clienteViewModel.profiloSuccesso.collectAsState()
    val passwordError by clienteViewModel.passwordError.collectAsState()
    val passwordSuccesso by clienteViewModel.passwordSuccesso.collectAsState()

    LaunchedEffect(Unit) { clienteViewModel.clearImpostazioniMessages() }

    Scaffold(
        topBar = {
            com.romanopetroli.rpfidelity.ui.theme.RpTopBar(
                title = "Impostazioni",
                navigationIcon = Icons.Filled.Menu,
                onNavigationClick = onOpenDrawer,
                navigationContentDescription = "Apri menu"
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("I miei dati", fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = nome, onValueChange = { nome = it }, label = { Text("Nome") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    OutlinedTextField(
                        value = cognome, onValueChange = { cognome = it }, label = { Text("Cognome") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = email, onValueChange = { email = it }, label = { Text("Email") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = telefono, onValueChange = { telefono = it }, label = { Text("Telefono") },
                        singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )

                    if (!profiloError.isNullOrBlank()) {
                        Text(profiloError ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    }
                    if (!profiloSuccesso.isNullOrBlank()) {
                        Text(profiloSuccesso ?: "", color = RpOrange, modifier = Modifier.padding(top = 12.dp))
                    }

                    Button(
                        onClick = {
                            clienteViewModel.aggiornaProfilo(nome, cognome, email, telefono) {
                                sessionViewModel.refreshUser()
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Salva dati") }
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cambia password", fontWeight = FontWeight.Bold)

                    OutlinedTextField(
                        value = passwordAttuale, onValueChange = { passwordAttuale = it },
                        label = { Text("Password attuale") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                    OutlinedTextField(
                        value = nuovaPassword, onValueChange = { nuovaPassword = it },
                        label = { Text("Nuova password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = confermaPassword, onValueChange = { confermaPassword = it },
                        label = { Text("Conferma nuova password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )

                    if (!passwordError.isNullOrBlank()) {
                        Text(passwordError ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
                    }
                    if (!passwordSuccesso.isNullOrBlank()) {
                        Text(passwordSuccesso ?: "", color = RpOrange, modifier = Modifier.padding(top = 12.dp))
                    }

                    Button(
                        onClick = {
                            clienteViewModel.aggiornaPassword(passwordAttuale, nuovaPassword, confermaPassword) {
                                passwordAttuale = ""
                                nuovaPassword = ""
                                confermaPassword = ""
                            }
                        },
                        enabled = !loading && passwordAttuale.isNotBlank() && nuovaPassword.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    ) { Text("Cambia password") }
                }
            }

            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
            }
        }
    }
}
