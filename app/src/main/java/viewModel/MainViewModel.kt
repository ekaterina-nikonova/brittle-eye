package viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.brittlepins.brittleeye.R

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val prompt = MutableLiveData<String>()
    private val context = app

    init {
        prompt.value = context.getString(R.string.prompt_start)
    }
}