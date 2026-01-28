package com.zyra.app.logic

import com.zyra.app.data.Contact

sealed class InteractionState {
    object Idle : InteractionState()
    
    data class ClarifyContact(
        val candidates: List<Contact>
    ) : InteractionState()
    
    data class ClarifyApp(
        val candidates: List<AppCandidate>,
        val action: String?
    ) : InteractionState()
    
    data class ConfirmWebSearch(
        val query: String
    ) : InteractionState()
    
    data class ConfirmPlayStoreSearch(
        val appName: String
    ) : InteractionState()
    
    data class ConfirmCall(
        val name: String,
        val number: String
    ) : InteractionState()

    object IdentifyUser : InteractionState()
    
    object VerifyAuthCode : InteractionState()
    
    object IncomingCall : InteractionState()
}

data class AppCandidate(
    val label: String,
    val packageName: String,
    val score: Int
)

class ConversationManager {
    
    var currentState: InteractionState = InteractionState.Idle
        private set
        
    fun startContactClarification(candidates: List<Contact>) {
        currentState = InteractionState.ClarifyContact(candidates)
    }
    
    fun startAppClarification(candidates: List<AppCandidate>, action: String?) {
        currentState = InteractionState.ClarifyApp(candidates, action)
    }
    
    fun startCallConfirmation(name: String, number: String) {
        currentState = InteractionState.ConfirmCall(name, number)
    }
    
    fun startWebSearchConfirmation(query: String) {
        currentState = InteractionState.ConfirmWebSearch(query)
    }
    
    fun startIncomingCall() {
        currentState = InteractionState.IncomingCall
    }
    
    fun startPlayStoreSearchConfirmation(appName: String) {
        currentState = InteractionState.ConfirmPlayStoreSearch(appName)
    }
    
    fun startUserIdentification() {
        currentState = InteractionState.IdentifyUser
    }
    
    fun startAuthCodeVerification() {
        currentState = InteractionState.VerifyAuthCode
    }
    
    fun clear() {
        currentState = InteractionState.Idle
    }
    
    fun isBusy(): Boolean = currentState !is InteractionState.Idle
}
