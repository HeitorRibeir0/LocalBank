package com.localbank.finance.auth

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Gerencia autenticação com Google Sign-In via Firebase Auth.
 * Usa o método clássico (GoogleSignIn API) que funciona em todos os dispositivos.
 */
object AuthManager {

    // Web Client ID (client_type 3) do google-services.json
    private const val WEB_CLIENT_ID =
        "347137707608-en24uscqqkb5do75tungjpce11a535r2.apps.googleusercontent.com"

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    val currentUser: FirebaseUser?
        get() = auth.currentUser

    val isSignedIn: Boolean
        get() = auth.currentUser != null

    val displayName: String
        get() = auth.currentUser?.displayName ?: "Usuário"

    val userId: String?
        get() = auth.currentUser?.uid

    /**
     * Retorna o Intent de login do Google para ser lançado com ActivityResultLauncher.
     */
    fun getSignInIntent(context: Context): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        val client = GoogleSignIn.getClient(context, gso)
        return client.signInIntent
    }

    /**
     * Processa o ID Token retornado pelo Google e autentica no Firebase.
     */
    suspend fun firebaseAuthWithGoogle(idToken: String): FirebaseUser {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = auth.signInWithCredential(credential).await()
        return result.user ?: throw Exception("Falha na autenticação")
    }

    fun signOut(context: Context) {
        auth.signOut()
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(WEB_CLIENT_ID)
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso).signOut()
    }
}
