package id.jsn.vidcut.dubbing

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class TigerModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            TigerModelManager.downloadAll(applicationContext) { model, percent ->
                setProgress(workDataOf("model" to model, "percent" to percent))
            }
            Result.success()
        } catch (t: Throwable) {
            Result.failure(workDataOf("error" to (t.message ?: t.javaClass.simpleName)))
        }
    }
}
