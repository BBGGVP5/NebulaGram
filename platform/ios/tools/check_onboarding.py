"""Integration guards for real patched sources. Not a native build/device test."""
from pathlib import Path
import re
import subprocess


def function(source, name):
    match = re.search(r'\bfunc ' + re.escape(name) + r'\(', source)
    assert match, name
    start = source.index('{', match.start())
    depth = 1
    end = start + 1
    while depth and end < len(source):
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    assert depth == 0
    return source[match.start():end]


def check(temp, vendor, revision):
    folder = 'submodules/AuthorizationUI/Sources/'
    def original(name):
        return subprocess.check_output(['git', '-C', str(vendor), 'show', revision + ':' + folder + name], text=True, encoding='utf-8')
    def patched(name): return (temp / folder / name).read_text(encoding='utf-8')
    name = 'AuthorizationSequenceSplashController.swift'
    welcome = patched(name)
    assert 'NebulaWelcomeController(' in welcome
    assert 'NebulaLinkController(' in welcome
    for handler in ['activateLocalization', 'pressNext']:
        assert function(welcome, handler) == function(original(name), handler), handler
    for step in ['Phone', 'Code', 'Password']:
        name = f'AuthorizationSequence{step}EntryControllerNode.swift'
        old, new = original(name), patched(name)
        assert 'AuthorizationLayoutItem(node: self.nebulaArtwork,' in new
        assert 'transition.updateFrame(node: self.nebulaBackdrop' in new
        assert 'self.nebulaBackdrop.setFieldFrame(' in new
        assert 'self.nebulaArtwork.isHidden = true' in new  # compact/keyboard constraints retained
        for handler in ['activateInput', 'animateError']:
            assert function(old, handler) == function(new, handler), (step, handler)
        # Native code delivery, recovery, password input, errors and accessibility remain native.
        for identifier in re.findall(r'"Auth\.[^"]+"', old): assert identifier in new
        for handler in ['textField', 'textDidChange', 'passwordIsInvalid', 'resetPressed', 'forgotPressed']:
            if re.search(r'\bfunc ' + handler + r'\(', old):
                assert function(old, handler) == function(new, handler), (step, handler)
    code = patched('AuthorizationSequenceCodeEntryControllerNode.swift')
    for label in ['Login_EnterWordTitle', 'Login_EnterPhraseTitle', 'Login_EnterCodeTelegramTitle', 'Login_EnterCodeSMSTitle']:
        assert label in code
    presentation = patched('NebulaAuthPresentation.swift')
    assert 'isUserInteractionEnabled = false' in presentation
    assert 'reduceTransparencyStatusDidChangeNotification' in presentation
    assert 'CGPoint(x: 148, y: 52)' in presentation
    welcome_ui = patched('NebulaWelcomeController.swift')
    assert 'scroll.contentSize' in welcome_ui and 'view.safeAreaInsets.bottom' in welcome_ui
    assert 'isReduceMotionEnabled' in welcome_ui
    assert 'languageChanged?(selectedLanguage)' in welcome_ui
    service = (temp / 'submodules/NebulaLinkUI/Sources/NebulaLinkService.swift').read_text(encoding='utf-8')
    for required in ['NebulalinkCall(method, json)', 'updateProxySettingsInteractively', '127.0.0.1',
                     'kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly', 'current.activeServer == owned',
                     'enqueueRoute', 'isExcludedFromBackup = true']:
        assert required in service, required
    assert 'NEPacketTunnelProvider' not in service
    ui = (temp / 'submodules/NebulaLinkUI/Sources/NebulaLinkController.swift').read_text(encoding='utf-8')
    for command in ['onboarding.connect', 'servers.list', 'server.select', 'tunnel.start', 'tunnel.stop', 'probe.url', 'subscription.refreshAll']:
        assert '"' + command + '"' in ui
    assert 'String(describing: error)' not in service + ui
    shared = (temp / 'submodules/TelegramUI/Sources/SharedAccountContext.swift').read_text(encoding='utf-8')
    assert 'NebulaLinkService.shared.configure(accountManager: accountManager)' in shared
    print('OK: Nebula onboarding, native auth handlers, proxy lifecycle and privacy integration guards')
