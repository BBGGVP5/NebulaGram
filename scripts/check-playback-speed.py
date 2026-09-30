"""Execute the patched MediaController setter against a player with a retained rate."""
from pathlib import Path
import subprocess
import sys
import tempfile

source = (Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org/telegram/messenger/MediaController.java').read_text(encoding='utf-8')
start = source.index('    public void setPlaybackSpeed(boolean music, float speed) {')
end = source.index('\n    public float getPlaybackSpeed(', start)
setter = source[start:end]
# Exercise start-of-track rate assignment, including the reset to 1x.
assert 'if (Math.abs(currentPlaybackSpeed - 1.0f)' not in source
assert 'audioPlayer.setPlaybackSpeed(Math.round(currentMusicPlaybackSpeed * 10f) / 10f);\n                    try {' in source
java = r'''
class PlaybackCheck {
 static class MessageObject {float audioProgress;boolean music;boolean isMusic(){return music;}}
 static class Player {float rate=2f;int changes;void pause(){}void play(){}void setPlaybackSpeed(float v){rate=v;changes++;}}
 static class AndroidUtilities {static void runOnUIThread(Runnable r,int delay){r.run();}}
 static class Preferences {Preferences edit(){return this;}Preferences putFloat(String k,float v){return this;}void commit(){}}
 static class MessagesController {static Preferences getGlobalMainSettings(){return new Preferences();}}
 static class NotificationCenter {static int messagePlayingSpeedChanged;static NotificationCenter getGlobalInstance(){return new NotificationCenter();}void postNotificationName(int id){}}
 static class CastSync {static void setSpeed(float v){}}
 float currentPlaybackSpeed=1f,currentMusicPlaybackSpeed=1f,fastPlaybackSpeed=2f,fastMusicPlaybackSpeed=2f;
 boolean isPaused,ignorePlayerUpdate;MessageObject playingMessageObject=new MessageObject();Player audioPlayer=new Player(),videoPlayer;
 boolean isSamePlayingMessage(MessageObject m){return m==playingMessageObject;}
 void seekToProgress(MessageObject m,float p){}
 SETTER
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  for(boolean music:new boolean[]{false,true})for(float rate:new float[]{.5f,1f,1.5f,2f,3f}){
   PlaybackCheck c=new PlaybackCheck();c.playingMessageObject.music=music;
   c.setPlaybackSpeed(music,rate);check(c.audioPlayer.rate==rate);
   c.setPlaybackSpeed(!music,3f);check(c.audioPlayer.rate==rate);
   c.setPlaybackSpeed(music,1f);check(c.audioPlayer.rate==1f);
   c.videoPlayer=c.audioPlayer;c.audioPlayer=null;c.setPlaybackSpeed(music,rate);check(c.videoPlayer.rate==rate);
   c.setPlaybackSpeed(music,1f);check(c.videoPlayer.rate==1f);
  }
  PlaybackCheck c=new PlaybackCheck();c.currentMusicPlaybackSpeed=13f;c.setPlaybackSpeed(true,1f);
  check(c.audioPlayer.rate==2f); // inactive music setting cannot change the active voice note
  c.audioPlayer=null;c.videoPlayer=new Player();c.currentMusicPlaybackSpeed=13f;c.setPlaybackSpeed(true,1f);
  check(c.videoPlayer.rate==2f);
  for(float invalid:new float[]{Float.NaN,Float.POSITIVE_INFINITY,0f,-1f})c.setPlaybackSpeed(false,invalid);
  check(c.videoPlayer.changes==0);
  System.out.println("Playback rate: 1x reset, voice/music isolation, both player types and invalid input passed");
 }
}
'''.replace('SETTER', setter)
with tempfile.TemporaryDirectory(prefix='nebula-playback-') as temp:
    file=Path(temp)/'PlaybackCheck.java'
    file.write_text(java,encoding='utf-8')
    subprocess.run(['javac','-encoding','UTF-8',str(file)],check=True)
    subprocess.run(['java','-cp',temp,'PlaybackCheck'],check=True)
