package com.localbank.finance.auth

import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/** Login com Google via Credential Manager + Firebase Auth. */
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
     * Abre o seletor de contas do Google e entra no Firebase.
     * [activityContext] precisa ser uma Activity (o seletor é uma tela por cima dela).
     * Lança GetCredentialCancellationException se a pessoa fechar o seletor.
     */
    suspend fun signInWithGoogle(activityContext: Context): FirebaseUser {
        // GetSignInWithGoogleOption é o fluxo do botão "Entrar com Google": mostra todas as contas,
        // inclusive as que nunca entraram no app
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(WEB_CLIENT_ID).build())
            .build()
        val credential = CredentialManager.create(activityContext)
            .getCredential(activityContext, request)
            .credential

        check(credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            "Credencial inesperada: ${credential.type}"
        }
        val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val result = auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
        return result.user ?: throw IllegalStateException("Falha na autenticação")
    }

    suspend fun signOut(context: Context) {
        auth.signOut()
        // Sem isso o próximo login entraria direto na mesma conta, sem perguntar
        CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
    }
}
