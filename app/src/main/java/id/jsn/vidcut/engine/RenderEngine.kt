package id.jsn.vidcut.engine
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Transformer
import java.io.File
class Media3RenderEngine(private val context:Context){
 fun trim(source:Uri,out:File,startMs:Long,endMs:Long,onDone:()->Unit,onError:(Exception)->Unit){
  val item=MediaItem.Builder().setUri(source).setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(startMs).setEndPositionMs(endMs).build()).build()
  val edited=EditedMediaItem.Builder(item).build()
  val t=Transformer.Builder(context).addListener(object:Transformer.Listener{
   override fun onCompleted(c:androidx.media3.transformer.Composition,r:androidx.media3.transformer.ExportResult){onDone()}
   override fun onError(c:androidx.media3.transformer.Composition,r:androidx.media3.transformer.ExportResult,e:androidx.media3.transformer.ExportException){onError(e)}
  }).build()
  out.parentFile?.mkdirs();t.start(edited,out.absolutePath)
 }
}
