import app.nebulagram.ui.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public final class AudioProtocolCheck {
    static int checks;
    static void check(boolean yes, String description) { checks++; if (!yes) throw new AssertionError(description); }
    interface Work { void run() throws Exception; }
    static void rejects(Work work) throws Exception { try { work.run(); throw new AssertionError("Invalid input accepted"); } catch (IOException | JSONException | IllegalArgumentException expected) { checks++; } }
    static final class Fixture extends HttpURLConnection {
        final ByteArrayOutputStream sent = new ByteArrayOutputStream(); final Map<String,String> headers = new HashMap<>();
        byte[] response; int code=200; long contentLength=-1;
        Fixture(URL url) { super(url); }
        public void connect() { }
        public void disconnect() { }
        public boolean usingProxy() { return false; }
        public void setRequestProperty(String key,String value) { headers.put(key,value); }
        public OutputStream getOutputStream() { return sent; }
        public int getResponseCode() { return code; }
        public int getContentLength() { return (int)contentLength; }
        public InputStream getInputStream() { return new ByteArrayInputStream(response); }
    }
    static final ArrayList<Fixture> requests = new ArrayList<>();
    static byte[] next; static int nextCode=200; static long nextLength=-1;
    static JSONObject gemini(String type, Object data) throws Exception { return new JSONObject().put("status","completed").put("outputs",new JSONArray().put(new JSONObject().put("type",type).put(type.equals("audio")?"data":"text",data))); }
    public static void main(String[] args) throws Exception {
        URL.setURLStreamHandlerFactory(protocol -> protocol.equals("https") ? new URLStreamHandler() { protected URLConnection openConnection(URL url) { Fixture f=new Fixture(url);f.response=next;f.code=nextCode;f.contentLength=nextLength;requests.add(f);return f; } } : null);
        byte[] media = {0,1,2,(byte)255,13,10}; String boundary="nebula-test-boundary";
        byte[] multipart=NebulaAudioProtocol.multipart(media,"audio/ogg","gpt-4o-mini-transcribe",boundary);
        String wire=new String(multipart,StandardCharsets.ISO_8859_1);
        check(wire.contains("filename=\"recording.ogg\"") && wire.contains("Content-Type: audio/ogg"),"OGG filename and MIME");
        check(wire.contains("name=\"model\"\r\n\r\ngpt-4o-mini-transcribe") && wire.contains("name=\"response_format\"\r\n\r\njson"),"transcription fields");
        check(wire.contains(new String(media,StandardCharsets.ISO_8859_1)),"multipart preserves binary data");
        check(new String(NebulaAudioProtocol.multipart(media,"video/mp4","whisper-1",boundary),StandardCharsets.ISO_8859_1).contains("recording.mp4"),"video format metadata");
        rejects(()->NebulaAudioProtocol.multipart(media,"text/html","m",boundary));
        rejects(()->NebulaAudioProtocol.multipart(media,"audio/ogg","m\r\nInjected: x",boundary));
        rejects(()->NebulaAudioProtocol.multipart(new byte[0],"audio/ogg","m",boundary));
        JSONObject payload=NebulaAudioProtocol.transcription(media,"audio/ogg","models/gemini-audio");
        check(!payload.getBoolean("store") && payload.getString("model").equals("gemini-audio"),"Gemini model and retention");
        check(Arrays.equals(Base64.getDecoder().decode(payload.getJSONArray("input").getJSONObject(1).getString("data")),media),"Gemini preserves source");
        check(NebulaAudioProtocol.transcript(0,new JSONObject("{\"text\":\" Привет \"}")).equals("Привет"),"OpenAI transcript");
        rejects(()->NebulaAudioProtocol.transcript(0,new JSONObject("{\"error\":{},\"text\":\"bad\"}")));
        JSONObject result=gemini("text","Hello");
        check(NebulaAudioProtocol.transcript(2,result).equals("Hello"),"Gemini outputs");
        JSONObject steps=new JSONObject("{\"status\":\"completed\",\"steps\":[{\"type\":\"user_input\",\"content\":[{\"type\":\"text\",\"text\":\"input\"}]},{\"type\":\"model_output\",\"content\":[{\"type\":\"thought\",\"text\":\"hidden\"},{\"type\":\"text\",\"text\":\"answer\"}]}]}");
        check(NebulaAudioProtocol.transcript(2,steps).equals("answer"),"exclude input and thought steps");
        rejects(()->NebulaAudioProtocol.transcript(2,result.put("status","in_progress")));
        rejects(()->NebulaAudioProtocol.transcript(2,new JSONObject("{\"status\":\"completed\",\"outputs\":[{\"type\":\"text\",\"thought\":true,\"text\":\"hidden\"}]}")));
        byte[] pcm={0,0,1,0}; byte[] wav=NebulaAudioProtocol.pcmWave(pcm);
        check(wav.length==48 && ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN).getInt(24)==24000,"PCM WAV sample rate");
        check(Arrays.equals(Arrays.copyOfRange(wav,44,wav.length),pcm),"PCM bytes preserved");
        check(Arrays.equals(NebulaAudioProtocol.wave(wav),wav),"WAV unchanged");
        rejects(()->NebulaAudioProtocol.pcmWave(new byte[]{0})); rejects(()->NebulaAudioProtocol.wave("<html>not audio</html>".getBytes()));
        JSONObject audio=gemini("audio",Base64.getEncoder().encodeToString(wav));
        check(Arrays.equals(NebulaAudioProtocol.generatedAudio(audio),wav),"Gemini WAV output");
        audio.getJSONArray("outputs").getJSONObject(0).put("data",Base64.getEncoder().encodeToString(pcm)).put("mime_type","audio/l16");
        check(Arrays.equals(NebulaAudioProtocol.generatedAudio(audio),wav),"Gemini PCM output");
        audio.getJSONArray("outputs").getJSONObject(0).put("data","%%%bad"); rejects(()->NebulaAudioProtocol.generatedAudio(audio));
        JSONObject speech=NebulaAudioProtocol.speech(0,"gpt-4o-mini-tts","Привет","marin","warm",1);
        check(speech.getString("input").equals("Привет") && speech.getString("response_format").equals("wav"),"OpenAI speech exact text");
        check(speech.has("instructions"),"GPT speech style");
        check(!NebulaAudioProtocol.speech(0,"tts-1-hd","Hello","alloy","calm",1).has("instructions"),"legacy speech does not receive unsupported instructions");
        JSONObject gs=NebulaAudioProtocol.speech(2,"gemini-3.8-flash-tts","Привет","Kore","warm",.8);
        check(gs.getJSONObject("response_format").getString("type").equals("audio"),"Gemini audio output request");
        check(gs.getJSONArray("input").getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("text").equals("Привет"),"Gemini exact text");
        rejects(()->NebulaAudioProtocol.speech(0,"m"," ","marin","warm",1));
        rejects(()->NebulaAudioProtocol.speech(0,"m","hi","fake","warm",1));
        rejects(()->NebulaAudioProtocol.speech(0,"m","hi","marin","warm",Double.NaN));
        rejects(()->NebulaAudioProtocol.speech(0,"m",String.join("",Collections.nCopies(4001,"x")),"marin","warm",1));
        NebulaAudioClient.Configuration openai=new NebulaAudioClient.Configuration(0,"fixture-secret","gpt-4o-mini-transcribe","marin","neutral",1);
        File file=File.createTempFile("nebula-fixture",".ogg"); Files.write(file.toPath(),media);
        try {
            next="{\"text\":\"Hello\"}".getBytes(StandardCharsets.UTF_8);
            check(new NebulaAudioClient().transcribe(openai,file,"audio/ogg").equals("Hello"),"actual OpenAI transport");
            Fixture request=requests.get(requests.size()-1);
            check(request.getURL().getPath().equals("/v1/audio/transcriptions") && !request.getInstanceFollowRedirects(),"endpoint and redirects");
            check(request.headers.get("Authorization").equals("Bearer fixture-secret") && request.headers.get("Content-Type").startsWith("multipart/form-data"),"auth and multipart headers");
            next=wav;
            NebulaAudioClient.Configuration tts=new NebulaAudioClient.Configuration(0,"fixture-secret","gpt-4o-mini-tts","marin","neutral",1);
            check(Arrays.equals(new NebulaAudioClient().speech(tts,"Hello"),wav),"actual speech transport");
            int count=requests.size(); NebulaAudioClient cancelled=new NebulaAudioClient();cancelled.cancel();rejects(()->cancelled.speech(tts,"Hello"));check(requests.size()==count,"cancelled request never connects");
            nextCode=302;rejects(()->new NebulaAudioClient().speech(tts,"Hello"));nextCode=200;
            nextLength=NebulaAudioProtocol.MAX_AUDIO+1;rejects(()->new NebulaAudioClient().speech(tts,"Hello"));nextLength=-1;
            try(RandomAccessFile oversized=new RandomAccessFile(file,"rw")){oversized.setLength(NebulaAudioProtocol.MAX_INPUT+1);} count=requests.size();rejects(()->new NebulaAudioClient().transcribe(openai,file,"audio/ogg"));check(requests.size()==count,"oversize rejected before connecting");
        } finally { file.delete(); }
        System.out.println("OK: "+checks+" audio protocol and transport assertions");
    }
}
