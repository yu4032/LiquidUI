package com.hellovoid.liquidui.glass.notification;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class NotificationCoverageSuppressionContractTest {
    @Test
    public void sharedSessionBootstrapsEveryDirectExpandableRowFromNssl() throws Exception {
        String collector = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassNodeCollector.java"));
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java"));

        assertTrue(collector.contains("boolean isRow(Object value)"));
        assertTrue(session.contains("bootstrapRowsFromStack"));
        assertTrue(session.contains("stackGroup.getChildCount()"));
        assertTrue(session.contains("collector.isRow(child)"));
        assertTrue(session.contains("rows.putIfAbsent(child, Boolean.TRUE)"));
        int refresh = session.indexOf("private void refreshScene()");
        int bootstrap = session.indexOf("bootstrapRowsFromStack", refresh);
        int iterate = session.indexOf("for (Object row : new ArrayList<>(rows.keySet()))", refresh);
        assertTrue(refresh >= 0 && bootstrap > refresh && iterate > bootstrap);
    }

    @Test
    public void contentAuthorityRebindKeepsPresentedGlassSuppressionActive() throws Exception {
        String session = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java"));
        int start = session.indexOf("private void onContentAuthorityChanged");
        int end = session.indexOf("private void onPassBlurSourceChanged", start);
        assertTrue(start >= 0 && end > start);
        String body = session.substring(start, end);

        assertTrue(body.contains("active = false"));
        assertTrue(body.contains("renderer.onPassBlurContentAuthorityChanged(snapshot)"));
        assertFalse(body.contains("renderer.setAlpha(0f)"));
        assertFalse(body.contains("setShadeBlurSuppression(false)"));
        assertFalse(body.contains("materialController.restoreAll()"));
    }
}
