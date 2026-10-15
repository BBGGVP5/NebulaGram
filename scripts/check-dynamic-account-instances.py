"""Execute the production account registry without Android or real sessions."""
from pathlib import Path
import os
import shutil
import subprocess
import re
import sys
import tempfile

root = Path(__file__).resolve().parent.parent
source = root / 'platform/android/overlay/TMessagesProj/src/main/java/org/telegram/messenger/AccountInstanceRegistry.java'
java_home = Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-26.0.2'))
javac = shutil.which('javac') or str(java_home / 'bin/javac.exe')
java = shutil.which('java') or str(java_home / 'bin/java.exe')
harness = r'''
import org.telegram.messenger.AccountInstanceRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class RegistryCheck {
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static final class Item {
        final int id;
        final byte[] initialized = new byte[1024];
        Item(int id) { this.id = id; Arrays.fill(initialized, (byte) 41); }
    }
    public static void main(String[] args) throws Exception {
        AccountInstanceRegistry<Item> registry = new AccountInstanceRegistry<>();
        check(registry.peek(1000000) == null);
        check(registry.valuesSnapshot().isEmpty());
        AccountInstanceRegistry<Item> monitorRegistry = new AccountInstanceRegistry<>();
        monitorRegistry.get(5, id -> {
            check(Thread.holdsLock(monitorRegistry.lockFor(id)));
            return new Item(id);
        });
        Object monitor = registry.lockFor(0);
        check(monitor == registry.lockFor(0) && monitor != registry.lockFor(1));
        check(registry.valuesSnapshot().isEmpty());
        ExecutorService pool = Executors.newFixedThreadPool(16);
        AtomicInteger creations = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> jobs = new ArrayList<>();
        for (int t = 0; t < 16; t++) jobs.add(pool.submit(() -> {
            start.await();
            for (int round = 0; round < 8; round++) for (int id = 0; id < 128; id++) {
                Item item = registry.get(id, key -> { creations.incrementAndGet(); return new Item(key); });
                check(item.id == id && item == registry.peek(id));
                check(item.initialized[1023] == 41);
            }
            return null;
        }));
        start.countDown();
        for (Future<?> job : jobs) job.get(20, TimeUnit.SECONDS);
        check(creations.get() == 128);
        check(registry.valuesSnapshot().size() == 128);
        Set<Item> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int id = 0; id < 128; id++) identities.add(registry.peek(id));
        check(identities.size() == 128);
        check(registry.get(1000000, Item::new).id == 1000000);
        check(registry.get(Integer.MAX_VALUE, Item::new).id == Integer.MAX_VALUE);
        try { registry.get(-1, Item::new); throw new AssertionError(); }
        catch (IllegalArgumentException expected) {}
        try { registry.get(999, id -> { throw new IllegalStateException(); }); throw new AssertionError(); }
        catch (IllegalStateException expected) {}
        check(registry.peek(999) == null && registry.get(999, Item::new).id == 999);
        try { registry.get(998, id -> null); throw new AssertionError(); }
        catch (NullPointerException expected) {}
        check(registry.get(998, Item::new).id == 998);
        AccountInstanceRegistry<Item> otherCurrency = new AccountInstanceRegistry<>();
        check(otherCurrency.get(0, Item::new) != registry.peek(0));
        Item old = registry.peek(0);
        registry.clearIfSame(0, old);
        check(registry.peek(0) == null);
        Item replacement = registry.get(0, Item::new);
        check(replacement != old);
        registry.clearIfSame(0, old);
        check(registry.peek(0) == replacement);
        // A slow constructor must not hold a registry-wide lock for another account.
        CountDownLatch constructing = new CountDownLatch(1), release = new CountDownLatch(1);
        Future<Item> slow = pool.submit(() -> registry.get(700, id -> {
            constructing.countDown();
            try { release.await(10, TimeUnit.SECONDS); } catch (InterruptedException e) { throw new AssertionError(e); }
            return new Item(id);
        }));
        check(constructing.await(5, TimeUnit.SECONDS));
        try { check(pool.submit(() -> registry.get(701, Item::new)).get(3, TimeUnit.SECONDS).id == 701); }
        finally { release.countDown(); }
        check(slow.get(5, TimeUnit.SECONDS).id == 700);
        pool.shutdownNow();
        System.out.println("Registry: 16,384 concurrent lookups, 128 isolated accounts, sparse IDs, retry and publication passed");
    }
}
'''
with tempfile.TemporaryDirectory(prefix='nebula-accounts-') as directory:
    work = Path(directory)
    test = work / 'RegistryCheck.java'
    test.write_text(harness, encoding='utf-8')
    subprocess.run([javac, '--release', '8', '-d', str(work), str(source), str(test)], check=True)
    subprocess.run([java, '-cp', str(work), 'RegistryCheck'], check=True, timeout=45)

    if len(sys.argv) > 1:
        tree = Path(sys.argv[1]) / 'TMessagesProj/src/main/java/org'
        files, calls = [], []
        for path in tree.rglob('*.java'):
            text = path.read_text(encoding='utf-8')
            declarations = re.findall(r'private static final org\.telegram\.messenger\.AccountInstanceRegistry<[^\n]+;', text)
            if not declarations:
                continue
            name = path.stem
            pattern = r'public static ' + name + r' getInstance\((?:final )?int \w+' + (r', boolean ton' if name == 'StarsController' else '') + r'\) \{'
            match = re.search(pattern, text)
            assert match, path
            depth, end = 1, match.end()
            while depth:
                depth += (text[end] == '{') - (text[end] == '}')
                end += 1
            method = text[match.start():end]
            consumers = ''
            if name == 'NotificationCenter':
                begin = text.index('public static void sanitize() {')
                finish = text.index('\n    }', begin) + len('\n    }')
                consumers = ('static NotificationCenter globalInstance = new NotificationCenter(-1);\n'
                             'static int sanitized;\n'
                             'static void sanitizeInternal(NotificationCenter value) { if (value != null) sanitized++; }\n'
                             + text[begin:finish])
            if name == 'MessagesController':
                assert 'lockObjects[' not in text
                consumers = 'private int currentAccount;\n'
                for method_name, type_name, field in [
                    ('getBoostsController', 'ChannelBoostsController', 'channelBoostsControler'),
                    ('getStoriesController', 'StoriesController', 'storiesController'),
                    ('getSavedMessagesController', 'SavedMessagesController', 'savedMessagesController'),
                    ('getUnconfirmedAuthController', 'UnconfirmedAuthController', 'unconfirmedAuthController'),
                ]:
                    begin = text.index('public ' + type_name + ' ' + method_name + '() {')
                    finish = text.index('\n    }', begin) + len('\n    }')
                    consumers += (f'private volatile {type_name} {field};\n'
                                  f'static class {type_name} {{ final int account; {type_name}(int account) {{ this.account = account; }} }}\n'
                                  + text[begin:finish] + '\n')
                    calls.append(f'if (MessagesController.getInstance(id).{method_name}() != MessagesController.getInstance(id).{method_name}()) throw new AssertionError("{method_name}");')
                    calls.append(f'if (MessagesController.getInstance(id).{method_name}().account != id) throw new AssertionError("Child account ID");')
            extra_parameter = ', boolean ton' if name == 'StarsController' else ''
            item = work / (name + '.java')
            item.write_text('public class ' + name + ' {\n' + '\n'.join(declarations) + '\n'
                            + 'public final int account;\n'
                            + 'private ' + name + '(int account' + extra_parameter + ') { this.account = account; '
                            + ('this.currentAccount = account;' if name == 'MessagesController' else '') + ' }\n'
                            + method + '\n' + consumers + '\n}', encoding='utf-8')
            files.append(str(item))
            arg = ', false' if name == 'StarsController' else ''
            calls.append(f'if ({name}.getInstance(id{arg}).account != id || {name}.getInstance(id{arg}) != {name}.getInstance(id{arg})) throw new AssertionError("{name}");')
            if name == 'StarsController':
                calls.append('if (StarsController.getInstance(id, false) == StarsController.getInstance(id, true)) throw new AssertionError("Currencies shared");')
        assert len(files) == 35, len(files)
        test = work / 'ControllerFactoriesCheck.java'
        test.write_text('public class ControllerFactoriesCheck { public static void main(String[] args) { for (int id = 0; id < 128; id++) { '
                        + '\n'.join(calls) + ' } NotificationCenter.sanitize(); if (NotificationCenter.sanitized != 129) throw new AssertionError("Sanitize must visit existing accounts and the global center"); } }', encoding='utf-8')
        subprocess.run([javac, '--release', '8', '-cp', str(work), '-d', str(work), *files, str(test)], check=True)
        subprocess.run([java, '-cp', str(work), 'ControllerFactoriesCheck'], check=True, timeout=45)
        print('All 35 production singleton getters compiled and exercised with 128 synthetic IDs; currencies isolated')
