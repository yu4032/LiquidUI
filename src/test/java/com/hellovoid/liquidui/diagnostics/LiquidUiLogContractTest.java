package com.hellovoid.liquidui.diagnostics;

import org.junit.Test;

import static org.junit.Assert.*;

public class LiquidUiLogContractTest {
    @Test
    public void diagnosticsUseLiquidUiPrefix() {
        assertEquals("[LUI] target supported", LiquidUiLog.format("target supported"));
    }
}
