import app.nebulagram.ui.NebulaTasks;
import org.telegram.messenger.ApplicationLoader;
import org.json.*;
import java.nio.file.*;

public class TaskStoreCheck {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("nebula-task-check");
        ApplicationLoader.applicationContext.directory = directory.toFile();
        android.app.AlarmManager alarms = ApplicationLoader.applicationContext.alarms;
        try {
            JSONObject task = new JSONObject().put("id", "one").put("title", "Read").put("done", false).put("remind", System.currentTimeMillis()+60000);
            NebulaTasks.put(101, task);
            NebulaTasks.put(102, task);
            check(alarms.scheduled.size()==2, "reminders collided across accounts");
            task.put("title", "Edited").put("remind", System.currentTimeMillis()+120000);
            NebulaTasks.put(101, task);
            check(NebulaTasks.read(101).length()==1 && alarms.scheduled.size()==2, "edit duplicated a task or reminder");
            check("Read".equals(NebulaTasks.read(102).getJSONObject(0).getString("title")), "account isolation");
            task.put("done", true);
            NebulaTasks.put(101, task);
            check(alarms.scheduled.size()==1, "completed task still has reminder");
            NebulaTasks.delete(102, "one");
            check(NebulaTasks.read(102).length()==0 && alarms.scheduled.isEmpty(), "delete left a reminder");
            Path stored = directory.resolve("nebula-tasks-101.json");
            Files.writeString(stored, "invalid-data");
            try { NebulaTasks.put(101, task); throw new AssertionError("corruption silently overwritten"); } catch (JSONException expected) { }
            check("invalid-data".equals(Files.readString(stored)), "corrupt data not preserved");
            try { NebulaTasks.read(0); throw new AssertionError("signed-out account accepted"); } catch (IllegalStateException expected) { }
            task.put("done", false).put("remind", 1);
            NebulaTasks.schedule(101, task);
            check(alarms.scheduled.values().iterator().next()>System.currentTimeMillis(), "overdue reminder not recovered");
            check(alarms.idle, "modern alarm API not used");
            android.os.Build.VERSION.SDK_INT=21;
            NebulaTasks.schedule(101, task);
            check(!alarms.idle, "API 21 attempted unavailable idle API");
            System.out.println("Task checks passed: edits, reminder replacement/cancel, overdue recovery, account isolation and corrupt data preservation");
        } finally {
            try (java.util.stream.Stream<Path> files=Files.list(directory)) { for(Path p:(Iterable<Path>)files::iterator) Files.delete(p); }
            Files.delete(directory);
        }
    }
}
