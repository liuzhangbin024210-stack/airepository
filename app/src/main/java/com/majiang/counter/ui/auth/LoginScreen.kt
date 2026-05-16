package com.majiang.counter.ui.auth



import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.runtime.Composable

import androidx.compose.runtime.LaunchedEffect

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.saveable.rememberSaveable

import androidx.compose.runtime.setValue

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.lifecycle.compose.collectAsStateWithLifecycle

import com.majiang.counter.ui.AuthViewModel

import com.majiang.counter.ui.PlayerStrings



@Composable

fun LoginScreen(authVm: AuthViewModel) {

    var username by rememberSaveable { mutableStateOf("") }

    var password by rememberSaveable { mutableStateOf("") }

    val formState by authVm.formState.collectAsStateWithLifecycle()



    LaunchedEffect(Unit) {

        authVm.resetFormFeedback()

    }



    Box(

        modifier = Modifier

            .fillMaxSize()

            .verticalScroll(rememberScrollState()),

        contentAlignment = Alignment.Center,

    ) {

        AuthScaffold(

            title = PlayerStrings.AUTH_LOGIN_TITLE,

            subtitle = PlayerStrings.AUTH_LOGIN_SUBTITLE,

            username = username,

            onUsernameChange = { username = it },

            password = password,

            onPasswordChange = { password = it },

            busy = formState.busy,

            errorMessage = formState.errorMessage,

            primaryLabel = PlayerStrings.AUTH_LOGIN_BUTTON,

            onPrimary = { authVm.login(username, password) },

        )

    }

}

