package com.romanopetroli.rpfidelity.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.romanopetroli.rpfidelity.R
import com.romanopetroli.rpfidelity.ui.theme.RpNavy
import com.romanopetroli.rpfidelity.ui.theme.RpNavyDark
import com.romanopetroli.rpfidelity.ui.theme.RpOrange
import com.romanopetroli.rpfidelity.viewmodel.SessionViewModel

@Composable
fun ForgotPasswordScreen(
    sessionViewModel: SessionViewModel,
    onBack: () -> Unit
) {
    var email by remember { mutableStateOf("") }

    val loading by sessionViewModel.forgotPasswordLoading.collectAsState()
    val message by sessionViewModel.forgotPasswordMessage.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(RpNavy, RpNavyDark))),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_rpfidelity),
                    contentDescription = "RP Fidelity",
                    modifier = Modifier.size(100.dp)
                )
                Text("Password dimenticata", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RpNavy)
                Text(
                    "Inserisci la tua email: ti invieremo un link per reimpostarla",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (message != null) {
                    Text(
                        text = message ?: "",
                        color = RpNavy,
                        modifier = Modifier.padding(top = 20.dp)
                    )
                } else {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    )

                    Button(
                        onClick = { sessionViewModel.forgotPassword(email.trim()) },
                        enabled = !loading && email.isNotBlank(),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = RpOrange),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 20.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        } else {
                            Text("Invia il link di recupero")
                        }
                    }
                }

                TextButton(
                    onClick = {
                        sessionViewModel.clearForgotPasswordMessage()
                        onBack()
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("← Torna al login", color = RpOrange)
                }
            }
        }
    }
}
