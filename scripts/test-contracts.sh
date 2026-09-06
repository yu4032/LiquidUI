#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/stubs/org/junit" "$WORK/classes"
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
    public static void assertTrue(boolean value) { if (!value) throw new AssertionError("expected true"); }
    public static void assertFalse(boolean value) { if (value) throw new AssertionError("expected false"); }
    public static void assertNull(Object value) { if (value != null) throw new AssertionError("expected null but was "+value); }
    public static void assertNotNull(Object value) { if (value == null) throw new AssertionError("expected non-null"); }
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
for f in NotificationGlassNode.java NotificationGlassSceneSnapshot.java NotificationGlassSceneState.java ZeroCopyProducerRecoveryState.java Miuix307BackdropMapping.java NotificationGlassActivityState.java NotificationShadeBlurPolicy.java NotificationPassBlurAuthorityState.java NotificationPassBlurSourceState.java; do
  PURE_MAIN+=("$ROOT/src/main/java/com/hellovoid/liquidui/glass/notification/$f")
done
mapfile -t TESTS < <(find "$ROOT/src/test/java" -name '*.java' -print | sort)
javac --release 17 -d "$WORK/classes" \
  "$WORK/stubs/org/junit/Test.java" "$WORK/stubs/org/junit/Assert.java" "$WORK/TestRunner.java" \
  "${PURE_MAIN[@]}" "${TESTS[@]}"
mapfile -t TEST_CLASSES < <(find "$ROOT/src/test/java" -name '*Test.java' -print | sort | sed -e "s#^$ROOT/src/test/java/##" -e 's#/#.#g' -e 's#\.java$##')
(cd "$ROOT" && java -cp "$WORK/classes" TestRunner "${TEST_CLASSES[@]}")
test "$(cat "$ROOT/src/main/resources/META-INF/xposed/scope.list")" = "com.android.systemui"
grep -qx 'com.hellovoid.liquidui.ModuleMain' "$ROOT/src/main/resources/META-INF/xposed/java_init.list"
! grep -R -nE 'com\.miui\.home|com\.hellovoid\.liquiddock' "$ROOT/src/main" "$ROOT/build.gradle.kts" "$ROOT/settings.gradle.kts"
! grep -R -nE 'PixelCopy|ImageReader|glReadPixels|MediaProjection' "$ROOT/src/main/java/com/hellovoid/liquidui/glass"
echo "LiquidUI contract suite PASS"
