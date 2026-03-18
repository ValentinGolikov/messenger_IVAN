package com.example.ivan.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun ChatScreen(
    // TODO() Передать параметры, нужные для чата: список сообщений, ...
) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // TopBarBox
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(color = Color.Green)
            ) {
                Text(
                    text = "Стартовый чат",
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ChatColumn
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color = Color.Red)
            ) {

            }
        }
    }
}
