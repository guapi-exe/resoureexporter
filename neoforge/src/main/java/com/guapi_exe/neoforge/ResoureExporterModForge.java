package com.guapi_exe.neoforge;

import net.neoforged.fml.common.Mod;

import com.guapi_exe.ResoureExporterMod;

@Mod(ResoureExporterMod.MOD_ID)
public final class ResoureExporterModForge {
    public ResoureExporterModForge() {
        // Architectury API for NeoForge does not require EventBuses.registerModEventBus anymore in 1.21+
        // if you are using the latest Architectury API.
        // However, if you need to register to the mod event bus, you can do it here.
        
        ResoureExporterMod.init();
    }
}
