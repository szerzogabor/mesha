package com.mesha.mobile.ui

import androidx.lifecycle.ViewModel
import com.mesha.mobile.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    val authState = authRepository.authState
    fun signOut() = authRepository.signOut()
}
