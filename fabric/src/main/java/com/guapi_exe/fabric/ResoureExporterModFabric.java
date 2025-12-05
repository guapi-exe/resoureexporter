package com.guapi_exe.fabric;

import net.fabricmc.api.ModInitializer;

import com.guapi_exe.ResoureExporterMod;

public final class ResoureExporterModFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        ResoureExporterMod.init();
    }
}
