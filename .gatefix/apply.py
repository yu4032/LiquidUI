from pathlib import Path
import re

renderer_path = Path('src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java')
s = renderer_path.read_text()

# Producer updates follow row/source demand. HyperOS notifPassBlur is observed only.
assert 'boolean effective = enabled && vendorPassBlurEnabled;' in s
s = s.replace('boolean effective = enabled && vendorPassBlurEnabled;', 'boolean effective = enabled;', 1)
s = s.replace('" vendorGate=" + vendorPassBlurEnabled + " reason=" + reason',
              '" vendorObserved=" + vendorPassBlurEnabled + " reason=" + reason', 1)

# Vendor authority callback becomes diagnostic-only; it may not activate/retire our producer.
start = s.index('    void setVendorPassBlurEnabled(boolean enabled, String reason) {')
end = s.index('    void onPassBlurSourceChanged(String reason) {', start)
replacement = '''    void setVendorPassBlurEnabled(boolean enabled, String reason) {\n        if (shuttingDown) return;\n        boolean changed = vendorPassBlurEnabled != enabled;\n        vendorPassBlurEnabled = enabled;\n        if (changed) {\n            log(" vendor PassBlur observed=" + enabled + " reason=" + reason\n                    + " (diagnostic only; LiquidUI producer is source-driven)");\n        }\n    }\n\n'''
s = s[:start] + replacement + s[end:]

# Remove the old activate/retire authority methods as a single exact region.
start = s.index('    private void activateVendorPassBlurAuthority(String reason) {')
end = s.index('    void rebindProducer(String reason) {', start)
s = s[:start] + s[end:]

# Source changes and producer recreation bind independently of vendor notifPassBlur.
s = s.replace('            if (vendorPassBlurEnabled) post(() -> bindProducerWhenReady(0));\n', '', 1)
assert 'if (recovery.requestBind && vendorPassBlurEnabled)' in s
s = s.replace('if (recovery.requestBind && vendorPassBlurEnabled)', 'if (recovery.requestBind)', 1)

initial_gate = '''            if (vendorPassBlurEnabled) {\n                post(() -> bindProducerWhenReady(0));\n            } else {\n                log(" vendor PassBlur closed; initial producer remains unbound");\n            }\n'''
assert initial_gate in s
s = s.replace(initial_gate, '            post(() -> bindProducerWhenReady(0));\n', 1)

# All async bind phases must remain generation/root guarded, but not vendor-gated.
s = s.replace(' || !vendorPassBlurEnabled', '')
s = s.replace('|| !vendorPassBlurEnabled\n', '\n')

# Hard-gate patterns are forbidden after the patch.
for forbidden in (
        '!vendorPassBlurEnabled',
        'recovery.requestBind && vendorPassBlurEnabled',
        'enabled && vendorPassBlurEnabled',
        'activateVendorPassBlurAuthority(',
        'retireVendorPassBlurAuthority('):
    assert forbidden not in s, forbidden
assert 'SystemUiPassBlurBridge.bind' in s
renderer_path.write_text(s)

test_path = Path('src/test/java/com/hellovoid/liquidui/architecture/NotificationSharedGlassArchitectureTest.java')
t = test_path.read_text()
start = t.index('    @Test\n    public void producerSamplingFollowsHyperOsNotifPassBlurAuthority()')
end = t.index('    @Test\n    public void vendorPassBlurAuthorityBootstrapsFromNotificationBlurProviderSnapshot()', start)
new_test = '''    @Test\n    public void liquidUiProducerBindDoesNotRequireHyperOsNotifPassBlurGate() throws Exception {\n        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");\n        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");\n        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");\n        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");\n        assertTrue(hook.contains("NotificationPassBlurAuthorityState"));\n        assertTrue(hook.contains("authorityState.observe(requested)"));\n        assertTrue(session.contains("authorityState.addListener"));\n        assertTrue(session.contains("setVendorPassBlurEnabled"));\n        assertTrue(renderer.contains("vendorPassBlurEnabled")); // diagnostics only\n        assertFalse(renderer.contains("!vendorPassBlurEnabled"));\n        assertFalse(renderer.contains("recovery.requestBind && vendorPassBlurEnabled"));\n        assertFalse(renderer.contains("enabled && vendorPassBlurEnabled"));\n        assertTrue(renderer.contains("SystemUiPassBlurBridge.bind"));\n        assertFalse(bridge.contains("setMiBlurWinExc"));\n        assertFalse(bridge.contains("exclusions"));\n    }\n\n'''
t = t[:start] + new_test + t[end:]
test_path.write_text(t)
