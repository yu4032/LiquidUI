#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/stubs/org/junit" "$WORK/stubs/android/os" "$WORK/stubs/android/view" \
         "$WORK/stubs/com/hellovoid/liquidui/glass/core" "$WORK/classes"
cat > "$WORK/stubs/org/junit/Test.java" <<'JAVA'
package org.junit;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Test {}
JAVA
cat > "$WORK/stubs/org/junit/Assert.java" <<'JAVA'
package org.junit;
public class Assert {
    public static void assertEquals(Object expected, Object actual) { if (expected == null ? actual != null : !expected.equals(actual)) throw new AssertionError("expected="+expected+" actual="+actual); }
    public static void assertEquals(long expected, long actual) { if (expected != actual) throw new AssertionError("expected="+expected+" actual="+actual); }
    public static void assertEquals(float expected, float actual, float delta) { if (Math.abs(expected-actual) > delta) throw new AssertionError("expected="+expected+" actual="+actual); }
    public static void assertTrue(boolean value) { if (!value) throw new AssertionError("expected true"); }
    public static void assertFalse(boolean value) { if (value) throw new AssertionError("expected false"); }
    public static void assertNull(Object value) { if (value != null) throw new AssertionError("expected null but was "+value); }
    public static void assertNotNull(Object value) { if (value == null) throw new AssertionError("expected non-null"); }
}
JAVA
cat > "$WORK/stubs/android/os/Looper.java" <<'JAVA'
package android.os;
public final class Looper {}
JAVA
cat > "$WORK/stubs/android/os/Handler.java" <<'JAVA'
package android.os;
public class Handler {
    public Handler(Looper looper) {}
    public boolean post(Runnable command) { command.run(); return true; }
}
JAVA
cat > "$WORK/stubs/android/os/HandlerThread.java" <<'JAVA'
package android.os;
public class HandlerThread {
    private final Looper looper = new Looper();
    public HandlerThread(String name) {}
    public void start() {}
    public Looper getLooper() { return looper; }
    public boolean quitSafely() { return true; }
}
JAVA
cat > "$WORK/stubs/android/view/Display.java" <<'JAVA'
package android.view;
public class Display {
    public static final int DEFAULT_DISPLAY = 0;
    public int getDisplayId() { return DEFAULT_DISPLAY; }
}
JAVA
cat > "$WORK/stubs/android/view/View.java" <<'JAVA'
package android.view;
public class View {
    public View getRootView() { return this; }
    public Display getDisplay() { return null; }
}
JAVA
cat > "$WORK/stubs/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java" <<'JAVA'
package com.hellovoid.liquidui.glass.core;
import android.view.View;
final class SystemUiPassBlurBridge {
    static Object getViewRootImpl(View view) { return null; }
}
JAVA
cat > "$WORK/stubs/com/hellovoid/liquidui/glass/core/WindowGlassSession.java" <<'JAVA'
package com.hellovoid.liquidui.glass.core;
import android.os.Handler;
import java.util.concurrent.atomic.AtomicBoolean;
public class WindowGlassSession implements AutoCloseable {
    private final WindowKey key;
    private final AtomicBoolean closed = new AtomicBoolean();
    public WindowGlassSession(WindowKey key) { this(key, null); }
    public WindowGlassSession(WindowKey key, Handler renderHandler) { this.key = key; }
    public WindowKey key() { return key; }
    public boolean isClosed() { return closed.get(); }
    @Override public void close() { closed.set(true); }
}
JAVA
cat > "$WORK/TestRunner.java" <<'JAVA'
import java.lang.reflect.*;
import org.junit.Test;
public final class TestRunner {
  public static void main(String[] args) throws Exception {
    int passed=0,failed=0;
    for(String className:args){
      Class<?> type=Class.forName(className); Object instance=type.getDeclaredConstructor().newInstance();
      for(Method method:type.getDeclaredMethods()) if(method.isAnnotationPresent(Test.class)) {
        try { method.invoke(instance); System.out.println("PASS "+className+"#"+method.getName()); passed++; }
        catch(InvocationTargetException e){ failed++; Throwable c=e.getCause(); System.err.println("FAIL "+className+"#"+method.getName()+": "+c); c.printStackTrace(System.err); }
      }
    }
    System.out.println("RESULT passed="+passed+" failed="+failed); if(failed!=0) System.exit(1);
  }
}
JAVA
mapfile -t PURE_MAIN < <(
  find "$ROOT/src/main/java/com/hellovoid/liquidui/target" \
       "$ROOT/src/main/java/com/hellovoid/liquidui/hook" \
       "$ROOT/src/main/java/com/hellovoid/liquidui/reflect" \
       "$ROOT/src/main/java/com/hellovoid/liquidui/config" \
       "$ROOT/src/main/java/com/hellovoid/liquidui/diagnostics" \
       -name '*.java' -print | sort
)
# Phase 0 deliberately moves Android/Prismal-owned Window rendering classes into glass/core.
# Keep this fast layer mostly dependency-free: compile core state/model classes plus the process
# core against compile-only UI/session stubs. Real Android/renderer ownership is compiled by Gradle.
while IFS= read -r f; do
  if ! grep -qE '^import (android\.|com\.hellovoid\.prismal\.)' "$f"; then
    PURE_MAIN+=("$f")
  fi
done < <(find "$ROOT/src/main/java/com/hellovoid/liquidui/glass/core" -name '*.java' -print | sort)
PURE_MAIN+=("$ROOT/src/main/java/com/hellovoid/liquidui/glass/core/SystemUiGlassCore.java")
for f in NotificationGlassNode.java NotificationGlassSceneSnapshot.java NotificationGlassSceneState.java ZeroCopyProducerRecoveryState.java Miuix307BackdropMapping.java NotificationGlassActivityState.java NotificationShadeBlurPolicy.java NotificationPassBlurAuthorityState.java NotificationGlassPresentationState.java; do
  if [[ -f "$ROOT/src/main/java/com/hellovoid/liquidui/glass/notification/$f" ]]; then
    PURE_MAIN+=("$ROOT/src/main/java/com/hellovoid/liquidui/glass/notification/$f")
  fi
done
mapfile -t TESTS < <(find "$ROOT/src/test/java" -name '*.java' -print | sort)
javac --release 17 -d "$WORK/classes" \
  "$WORK/stubs/org/junit/Test.java" "$WORK/stubs/org/junit/Assert.java" \
  "$WORK/stubs/android/os/Looper.java" "$WORK/stubs/android/os/Handler.java" "$WORK/stubs/android/os/HandlerThread.java" \
  "$WORK/stubs/android/view/Display.java" "$WORK/stubs/android/view/View.java" \
  "$WORK/stubs/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java" \
  "$WORK/stubs/com/hellovoid/liquidui/glass/core/WindowGlassSession.java" \
  "$WORK/TestRunner.java" "${PURE_MAIN[@]}" "${TESTS[@]}"
mapfile -t TEST_CLASSES < <(find "$ROOT/src/test/java" -name '*Test.java' -print | sort | sed -e "s#^$ROOT/src/test/java/##" -e 's#/#.#g' -e 's#\.java$##')
(cd "$ROOT" && java -cp "$WORK/classes" TestRunner "${TEST_CLASSES[@]}")
test "$(cat "$ROOT/src/main/resources/META-INF/xposed/scope.list")" = "com.android.systemui"
grep -qx 'com.hellovoid.liquidui.ModuleMain' "$ROOT/src/main/resources/META-INF/xposed/java_init.list"
! grep -R -nE 'com\.miui\.home|com\.hellovoid\.liquiddock' "$ROOT/src/main" "$ROOT/build.gradle.kts" "$ROOT/settings.gradle.kts"
! grep -R -nE 'PixelCopy|ImageReader|glReadPixels|MediaProjection' "$ROOT/src/main/java/com/hellovoid/liquidui/glass"
echo "LiquidUI contract suite PASS"
