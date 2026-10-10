package app.nebulagram.ui;

import android.os.Build;
import android.os.ParcelFileDescriptor;
import com.google.mlkit.genai.common.FeatureStatus;
import com.google.mlkit.genai.common.DownloadStatus;
import com.google.mlkit.genai.common.audio.AudioSource;
import com.google.mlkit.genai.speechrecognition.*;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.TimeoutKt;
import kotlinx.coroutines.flow.Flow;
import org.telegram.messenger.ApplicationLoader;

import java.io.*;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Recording bytes go to the on-device recognizer; model download needs connectivity. */
public final class NebulaLocalTranscription {
    public interface Progress { void text(String value); void status(String value); }
    private final boolean advanced;
    private final Locale locale;
    private volatile boolean cancelled;
    private volatile Thread owner;
    private volatile ParcelFileDescriptor reader, writer;
    public NebulaLocalTranscription(boolean advanced, String locale) { this.advanced=advanced;this.locale=Locale.forLanguageTag(NebulaLocalAudioPolicy.locale(locale)); }
    public static boolean supported(boolean advanced) {
        return advanced ? NebulaLocalAudioPolicy.advancedDevice(Build.MANUFACTURER,Build.MODEL,Build.VERSION.SDK_INT) : Build.VERSION.SDK_INT>=31;
    }
    public void cancel() { cancelled=true;Thread thread=owner;if(thread!=null)thread.interrupt();closePipes(); }
    private SpeechRecognizer client() throws IOException {
        if(!supported(advanced))throw new IOException(advanced?"LOCAL_AUDIO_NANO_UNAVAILABLE":"LOCAL_AUDIO_UNAVAILABLE");
        SpeechRecognizerOptions.Builder options=new SpeechRecognizerOptions.Builder();options.setLocale(locale);options.setPreferredMode(advanced?SpeechRecognizerOptions.Mode.MODE_ADVANCED:SpeechRecognizerOptions.Mode.MODE_BASIC);
        return SpeechRecognition.INSTANCE.getClient(options.build());
    }
    private static <T> T blocking(long milliseconds, Function2<CoroutineScope, Continuation<? super T>,Object> action) throws InterruptedException {
        return BuildersKt.runBlocking(EmptyCoroutineContext.INSTANCE,(scope, continuation)->TimeoutKt.withTimeout(milliseconds,action,continuation));
    }
    private void check() throws InterruptedIOException { if(cancelled||Thread.currentThread().isInterrupted())throw new InterruptedIOException("Cancelled"); }
    public int checkStatus() throws Exception {
        if(!supported(advanced))return FeatureStatus.UNAVAILABLE;
        owner=Thread.currentThread();
        try(SpeechRecognizer recognizer=client()){check();return blocking(45000,(scope,continuation)->recognizer.checkStatus(continuation));}
        finally{owner=null;}
    }
    public int download(Progress progress) throws Exception {
        owner=Thread.currentThread();
        try(SpeechRecognizer recognizer=client()){
            check();int status=blocking(45000,(scope,continuation)->recognizer.checkStatus(continuation));
            if(status!=FeatureStatus.DOWNLOADABLE)return status;
            AtomicReference<Throwable> failed=new AtomicReference<>();AtomicBoolean completed=new AtomicBoolean();
            collect(recognizer.download(),1800000,value->{
                if(value instanceof DownloadStatus.DownloadFailed)failed.set(((DownloadStatus.DownloadFailed)value).getE());
                else if(value instanceof DownloadStatus.DownloadCompleted)completed.set(true);
                else if(value instanceof DownloadStatus.DownloadProgress && progress!=null)progress.status("DOWNLOAD:"+((DownloadStatus.DownloadProgress)value).getTotalBytesDownloaded());
            });
            if(failed.get()!=null)throw new IOException("LOCAL_AUDIO_DOWNLOAD_FAILED",failed.get());
            if(!completed.get())throw new IOException("LOCAL_AUDIO_DOWNLOAD_FAILED");
            return blocking(45000,(scope,continuation)->recognizer.checkStatus(continuation));
        }finally{owner=null;}
    }
    private interface Output<T>{void next(T value)throws Exception;}
    private <T> void collect(Flow<? extends T> flow,long timeout,Output<T> output)throws Exception {
        blocking(timeout,(scope,continuation)->flow.collect((value,downstream)->{
            try{check();output.next(value);return Unit.INSTANCE;}
            catch(Exception error){throw new java.util.concurrent.CompletionException(error);}
        },continuation));
    }
    public String transcribe(File recording, Progress progress) throws Exception {
        check();owner=Thread.currentThread();boolean gate=false;File pcm=null;SpeechRecognizer recognizer=null;Thread feeder=null;
        try{
            if(!supported(advanced))throw new IOException(advanced?"LOCAL_AUDIO_NANO_UNAVAILABLE":"LOCAL_AUDIO_UNAVAILABLE");
            NebulaNanoInferenceGate.acquire(()->cancelled||Thread.currentThread().isInterrupted(),true);gate=true;check();
            recognizer=client();final SpeechRecognizer active=recognizer;
            int status=blocking(45000,(scope,continuation)->active.checkStatus(continuation));
            if(status!=FeatureStatus.AVAILABLE)throw new IOException(status==FeatureStatus.DOWNLOADABLE?"LOCAL_AUDIO_DOWNLOAD_REQUIRED":status==FeatureStatus.DOWNLOADING?"LOCAL_AUDIO_DOWNLOADING":"LOCAL_AUDIO_UNAVAILABLE");
            if(progress!=null)progress.status("DECODING");
            File directory=new File(ApplicationLoader.applicationContext.getCacheDir(),"nebula-local-audio");if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Local audio cache unavailable");
            File[] old=directory.listFiles();if(old!=null)for(File file:old)if(file.isFile()&&file.getName().startsWith("pcm-")&&System.currentTimeMillis()-file.lastModified()>86400000L)file.delete();
            pcm=File.createTempFile("pcm-",".raw",directory);NebulaLocalAudioDecoder.decode(recording,pcm,()->cancelled);check();
            ParcelFileDescriptor[] pipe=ParcelFileDescriptor.createReliablePipe();reader=pipe[0];writer=pipe[1];
            final File audio=pcm;final ParcelFileDescriptor sink=writer;
            AtomicReference<Exception> feederError=new AtomicReference<>();AtomicBoolean fullyFed=new AtomicBoolean();
            feeder=new Thread(()->feed(audio,sink,fullyFed,feederError),"NebulaLocalAudioPipe");feeder.setDaemon(true);feeder.start();
            SpeechRecognizerRequest.Builder request=new SpeechRecognizerRequest.Builder();request.setAudioSource(AudioSource.fromPfd(reader));
            NebulaLocalTranscript transcript=new NebulaLocalTranscript();AtomicBoolean completed=new AtomicBoolean();
            if(progress!=null)progress.status("RECOGNIZING");
            collect(active.startRecognition(request.build()),(NebulaLocalAudioPolicy.MAX_SECONDS+60L)*1000,value->{
                if(value instanceof SpeechRecognizerResponse.ErrorResponse)throw new IOException("LOCAL_AUDIO_RECOGNITION_FAILED",((SpeechRecognizerResponse.ErrorResponse)value).getE());
                if(value instanceof SpeechRecognizerResponse.FinalTextResponse){String preview=transcript.segment(((SpeechRecognizerResponse.FinalTextResponse)value).getText());if(progress!=null)progress.text(preview);}
                else if(value instanceof SpeechRecognizerResponse.PartialTextResponse){String preview=transcript.partial(((SpeechRecognizerResponse.PartialTextResponse)value).getText());if(progress!=null)progress.text(preview);}
                else if(value instanceof SpeechRecognizerResponse.CompletedResponse)completed.set(true);
            });
            check();feeder.join(2000);
            if(feederError.get()!=null)throw feederError.get();
            if(!fullyFed.get()||!completed.get())throw new IOException("LOCAL_AUDIO_INCOMPLETE");
            return transcript.result();
        }finally{
            closePipes();if(feeder!=null){feeder.interrupt();try{feeder.join(2000);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}}
            if(recognizer!=null){boolean interrupted=Thread.interrupted();final SpeechRecognizer active=recognizer;try{blocking(3000,(scope,continuation)->active.stopRecognition(continuation));}catch(Exception ignored){}finally{try{active.close();}catch(RuntimeException ignored){}if(interrupted)Thread.currentThread().interrupt();}}
            if(pcm!=null)pcm.delete();owner=null;if(gate)NebulaNanoInferenceGate.release();
        }
    }
    private void feed(File pcm,ParcelFileDescriptor output,AtomicBoolean fullyFed,AtomicReference<Exception> failure){
        try(InputStream input=new FileInputStream(pcm);OutputStream out=new ParcelFileDescriptor.AutoCloseOutputStream(output)){
            NebulaPcmFeeder.write(input,out,()->cancelled);
            fullyFed.set(true);
        }catch(Exception error){failure.set(error);try{output.closeWithError("Local PCM stream stopped");}catch(IOException ignored){}}
    }
    private void closePipes(){ParcelFileDescriptor a=reader,b=writer;reader=null;writer=null;if(a!=null)try{a.close();}catch(IOException ignored){}if(b!=null)try{b.close();}catch(IOException ignored){}}
    public static String errorText(Throwable error){
        for(Throwable cause=error;cause!=null;cause=cause.getCause()){
            String message=cause.getMessage();if(message==null)continue;
            if(message.equals("LOCAL_AUDIO_NANO_UNAVAILABLE"))return NebulaText.text("Распознавание Gemini Nano требует поддерживаемый Pixel 10/11 и доступную модель AICore. Выберите локальное распознавание Android или другой сервис.","Gemini Nano speech requires a supported Pixel 10/11 and an available AICore model. Choose local Android recognition or another service.");
            if(message.equals("LOCAL_AUDIO_DOWNLOAD_REQUIRED"))return NebulaText.text("Скачайте модель в «Аудио и голоса → Локальная модель».","Download the model in Audio & voices → Local model.");
            if(message.equals("LOCAL_AUDIO_DOWNLOADING"))return NebulaText.text("Локальная модель ещё загружается. Дождитесь завершения.","The local model is downloading. Wait for completion.");
            if(message.equals("LOCAL_AUDIO_UNAVAILABLE"))return NebulaText.text("Эта локальная модель недоступна для выбранного языка на устройстве. Проверьте AICore и языковой пакет.","This local model is unavailable for the selected language on this device. Check AICore and the language pack.");
            if(message.equals("LOCAL_AUDIO_DURATION_LIMIT"))return NebulaText.text("Локально можно обработать запись до 10 минут.","Local recognition supports recordings up to 10 minutes.");
            if(message.equals("LOCAL_AUDIO_NO_SPEECH"))return NebulaText.text("В записи не удалось распознать речь. Проверьте выбранный язык.","No speech was recognized. Check the selected language.");
            if(message.equals("LOCAL_AUDIO_INCOMPLETE"))return NebulaText.text("Модель завершила обработку до конца записи. Результат не сохранён; попробуйте другой режим.","The model stopped before the recording ended. The result was not saved; try another mode.");
        }
        return NebulaText.text("Не удалось распознать запись локально. Проверьте доступность модели и язык. Запись не отправлялась в облако.","Local recognition failed. Check model availability and language. The recording was not sent to the cloud.");
    }
}
