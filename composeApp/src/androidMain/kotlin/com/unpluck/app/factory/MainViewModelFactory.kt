package com.unpluck.app.factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.unpluck.app.MainViewModel
import com.unpluck.app.data.SpaceDao

class MainViewModelFactory(
    private val application: Application,
    private val dao: SpaceDao
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        // Check if it's assignable from MainViewModel
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            // Pass application and dao to MainViewModel's constructor
            return MainViewModel(application, dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}